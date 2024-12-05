/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.vectorstore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.oracle.coherence.ai.DistanceAlgorithm;
import com.oracle.coherence.ai.DocumentChunk;
import com.oracle.coherence.ai.Float32Vector;
import com.oracle.coherence.ai.distance.CosineDistance;
import com.oracle.coherence.ai.distance.InnerProductDistance;
import com.oracle.coherence.ai.distance.L2SquaredDistance;
import com.oracle.coherence.ai.hnsw.HnswIndex;
import com.oracle.coherence.ai.index.BinaryQuantIndex;
import com.oracle.coherence.ai.search.SimilaritySearch;
import com.oracle.coherence.ai.util.Vectors;
import com.tangosol.net.NamedMap;
import com.tangosol.net.Session;
import com.tangosol.util.Filter;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.beans.factory.InitializingBean;

/**
 * <p>
 * Integration of Coherence Coherence 24.09+ as a Vector Store.
 * </p>
 * <p>
 * Coherence Coherence 24.09 (or later) provides numerous features useful for artificial
 * intelligence such as Vectors, Similarity search, HNSW indexes, and binary quantization.
 * </p>
 * <p>
 * This Spring AI Vector store supports the following features:
 * <ul>
 * <li>Vectors with unspecified or fixed dimensions</li>
 * <li>Distance type for similarity search (note that similarity threshold can be used
 * only with distance type COSINE and DOT when ingested vectors are normalized, see
 * forcedNormalization)</li>
 * <li>Vector indexes (HNSW or Binary Quantization)</li>
 * <li>Exact and Approximate similarity search</li>
 * <li>Filter expression evaluation</li>
 * </ul>
 *
 * @author Aleks Seovic
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class CoherenceVectorStore implements VectorStore, InitializingBean {

	public enum IndexType {

		/**
		 * No index, use brute force exact calculation.
		 */
		NONE,

		/**
		 * Use Binary Quantization-based vector index
		 */
		BINARY,

		/**
		 * Use HNSW-based vector index
		 */
		HNSW

	}

	public enum DistanceType {

		/**
		 * Default dist. It calculates the cosine distance between two vectors.
		 */
		COSINE,

		/**
		 * Inner product.
		 */
		IP,

		/**
		 * Euclidean distance between two vectors (squared).
		 */
		L2

	}

	public static final String DEFAULT_MAP_NAME = "spring-ai-documents";

	public static final DistanceType DEFAULT_DISTANCE_TYPE = DistanceType.COSINE;

	public static final CoherenceFilterExpressionConverter FILTER_EXPRESSION_CONVERTER = new CoherenceFilterExpressionConverter();

	/**
	 * The embedding model to use to create query embedding.
	 */
	private final EmbeddingModel embeddingModel;

	private final int dimensions;

	private final Session session;

	private NamedMap<DocumentChunk.Id, DocumentChunk> documentChunks;

	/**
	 * Map name where vectors will be stored.
	 */
	private String mapName = DEFAULT_MAP_NAME;

	/**
	 * Distance type to use for computing vector distances.
	 */
	private DistanceType distanceType = DEFAULT_DISTANCE_TYPE;

	private boolean forcedNormalization;

	private IndexType indexType = IndexType.NONE;

	public CoherenceVectorStore(EmbeddingModel embeddingModel, Session session) {
		this.embeddingModel = embeddingModel;
		this.session = session;
		this.dimensions = embeddingModel.dimensions();
	}

	public CoherenceVectorStore setMapName(String mapName) {
		this.mapName = mapName;
		return this;
	}

	public CoherenceVectorStore setDistanceType(DistanceType distanceType) {
		this.distanceType = distanceType;
		return this;
	}

	public CoherenceVectorStore setIndexType(IndexType indexType) {
		this.indexType = indexType;
		return this;
	}

	public CoherenceVectorStore setForcedNormalization(boolean forcedNormalization) {
		this.forcedNormalization = forcedNormalization;
		return this;
	}

	@Override
	public void add(final List<Document> documents) {
		Map<DocumentChunk.Id, DocumentChunk> chunks = new HashMap<>((int) Math.ceil(documents.size() / 0.75f));
		for (Document doc : documents) {
			doc.setEmbedding(this.embeddingModel.embed(doc));
			var id = toChunkId(doc.getId());
			var chunk = new DocumentChunk(doc.getContent(), doc.getMetadata(), toFloat32Vector(doc.getEmbedding()));
			chunks.put(id, chunk);
		}
		this.documentChunks.putAll(chunks);
	}

	@Override
	public Optional<Boolean> delete(final List<String> idList) {
		var chunkIds = idList.stream().map(this::toChunkId).toList();
		Map<DocumentChunk.Id, Boolean> results = this.documentChunks.invokeAll(chunkIds, entry -> {
			if (entry.isPresent()) {
				entry.remove(false);
				return true;
			}
			return false;
		});
		for (boolean r : results.values()) {
			if (!r) {
				return Optional.of(false);
			}
		}
		return Optional.of(true);
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {
		// From the provided query, generate a vector using the embedding model
		final Float32Vector vector = toFloat32Vector(this.embeddingModel.embed(request.getQuery()));

		Expression expression = request.getFilterExpression();
		final Filter<?> filter = expression == null ? null : FILTER_EXPRESSION_CONVERTER.convert(expression);

		var search = new SimilaritySearch<DocumentChunk.Id, DocumentChunk, float[]>(DocumentChunk::vector, vector,
				request.getTopK())
			.algorithm(getDistanceAlgorithm())
			.filter(filter);

		var results = this.documentChunks.aggregate(search);

		List<Document> documents = new ArrayList<>(results.size());
		for (var r : results) {
			if (this.distanceType != DistanceType.COSINE || (1 - r.getDistance()) >= request.getSimilarityThreshold()) {
				DocumentChunk.Id id = r.getKey();
				DocumentChunk chunk = r.getValue();
				chunk.metadata().put(DocumentMetadata.DISTANCE.value(), r.getDistance());
				documents.add(Document.builder()
					.id(id.docId())
					.content(chunk.text())
					.metadata(chunk.metadata())
					.score(1 - r.getDistance())
					.build());
			}
		}
		return documents;
	}

	private DistanceAlgorithm<float[]> getDistanceAlgorithm() {
		return switch (this.distanceType) {
			case COSINE -> new CosineDistance<>();
			case IP -> new InnerProductDistance<>();
			case L2 -> new L2SquaredDistance<>();
		};
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		this.documentChunks = this.session.getMap(this.mapName);
		switch (this.indexType) {
			case HNSW -> this.documentChunks
				.addIndex(new HnswIndex<>(DocumentChunk::vector, this.distanceType.name(), this.dimensions));
			case BINARY -> this.documentChunks.addIndex(new BinaryQuantIndex<>(DocumentChunk::vector));
		}
	}

	// ---- helpers ----

	private DocumentChunk.Id toChunkId(String id) {
		return new DocumentChunk.Id(id, 0);
	}

	/**
	 * Converts a list of Double values into a Coherence
	 * {@link com.oracle.coherence.ai.Float32Vector} object ready to be inserted.
	 * <p/>
	 * Optionally normalize the vector beforehand (see forcedNormalization).
	 * @param floats an array of Doubles to convert
	 * @return a {@code Vector} instance
	 */
	private Float32Vector toFloat32Vector(final float[] floats) {
		return new Float32Vector(this.forcedNormalization ? Vectors.normalize(floats) : floats);
	}

	String getMapName() {
		return this.mapName;
	}

}
