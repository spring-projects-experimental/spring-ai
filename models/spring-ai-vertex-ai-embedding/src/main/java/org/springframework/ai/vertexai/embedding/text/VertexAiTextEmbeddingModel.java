/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vertexai.embedding.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddigConnectionDetails;
import org.springframework.ai.vertexai.embedding.VertextAiEmbeddingUtility;
import org.springframework.ai.vertexai.embedding.VertextAiEmbeddingUtility.TextInstanceBuilder;
import org.springframework.ai.vertexai.embedding.VertextAiEmbeddingUtility.TextParametersBuilder;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictRequest;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.protobuf.Value;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class VertexAiTextEmbeddingModel extends AbstractEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(VertexAiTextEmbeddingModel.class);

	public final VertexAiTextEmbeddingOptions defaultOptions;

	private VertexAiEmbeddigConnectionDetails connectionDetails;

	public VertexAiTextEmbeddingModel(VertexAiEmbeddigConnectionDetails connectionDetails,
			VertexAiTextEmbeddingOptions defaultOpitons) {

		Assert.notNull(defaultOpitons, "VertexAiEmbeddingOptions must not be null");

		this.defaultOptions = defaultOpitons.initializeDefaults();

		this.connectionDetails = connectionDetails;
	}

	@Override
	public List<Double> embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document.getFormattedContent());
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {

		VertexAiTextEmbeddingOptions finalOptions = this.defaultOptions;

		if (request.getOptions() != null && request.getOptions() != EmbeddingOptions.EMPTY) {
			var defaultOptionsCopy = VertexAiTextEmbeddingOptions.builder().from(this.defaultOptions).build();
			finalOptions = ModelOptionsUtils.merge(request.getOptions(), defaultOptionsCopy,
					VertexAiTextEmbeddingOptions.class);
		}

		try (PredictionServiceClient client = PredictionServiceClient
			.create(this.connectionDetails.getPredictionServiceSettings())) {

			EndpointName endpointName = this.connectionDetails.getEndpointName(finalOptions.getModel());

			PredictRequest.Builder predictRequestBuilder = PredictRequest.newBuilder()
				.setEndpoint(endpointName.toString());

			TextParametersBuilder parametersBuilder = TextParametersBuilder.of().withAutoTruncate(true);

			if (finalOptions.getDimensions() != null) {
				parametersBuilder.withOutputDimensionality(finalOptions.getDimensions());
			}

			predictRequestBuilder.setParameters(VertextAiEmbeddingUtility.valueOf(parametersBuilder.build()));

			for (int i = 0; i < request.getInstructions().size(); i++) {

				TextInstanceBuilder instanceBuilder = TextInstanceBuilder.of(request.getInstructions().get(i))
					.withTaskType(finalOptions.getTaskType().name());
				if (StringUtils.hasText(finalOptions.getTitle())) {
					instanceBuilder.withTitle(finalOptions.getTitle());
				}
				predictRequestBuilder.addInstances(VertextAiEmbeddingUtility.valueOf(instanceBuilder.build()));
			}

			PredictResponse embeddingResponse = client.predict(predictRequestBuilder.build());

			int index = 0;
			int totalTokenCount = 0;
			List<Embedding> embeddingList = new ArrayList<>();
			for (Value prediction : embeddingResponse.getPredictionsList()) {
				Value embeddings = prediction.getStructValue().getFieldsOrThrow("embeddings");
				Value statistics = embeddings.getStructValue().getFieldsOrThrow("statistics");
				Value tokenCount = statistics.getStructValue().getFieldsOrThrow("token_count");
				totalTokenCount = totalTokenCount + Double.valueOf(tokenCount.getNumberValue()).intValue();

				Value values = embeddings.getStructValue().getFieldsOrThrow("values");

				List<Double> vectorValues = VertextAiEmbeddingUtility.toVector(values);

				embeddingList.add(new Embedding(vectorValues, index++));
			}
			return new EmbeddingResponse(embeddingList,
					generateResponseMetadata(finalOptions.getModel(), totalTokenCount));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private EmbeddingResponseMetadata generateResponseMetadata(String model, Integer tokenCount) {
		EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata();
		metadata.put("model", model);
		metadata.put("total-tokens", tokenCount);
		return metadata;
	}

	@Override
	public int dimensions() {
		return KNOWN_EMBEDDING_DIMENSIONS.getOrDefault(this.defaultOptions.getModel(), super.dimensions());
	}

	private static final Map<String, Integer> KNOWN_EMBEDDING_DIMENSIONS = Stream
		.of(VertexAiTextEmbeddingModelName.values())
		.collect(Collectors.toMap(model -> model.getTextName(), model -> model.getDimensions()));

}