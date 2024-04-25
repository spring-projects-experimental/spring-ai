/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.vectorstore.elasticsearch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Eddú Meléndez
 * @author Wei Jiang
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "spring.ai.vectorstore.elasticsearch")
public class ElasticsearchVectorStoreProperties {

	private String indexName;

	private Integer dims;

	private Boolean denseVectorIndexing;

	private String similarity;

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public Integer getDims() {
		return dims;
	}

	public void setDims(Integer dims) {
		this.dims = dims;
	}

	public Boolean isDenseVectorIndexing() {
		return denseVectorIndexing;
	}

	public void setDenseVectorIndexing(Boolean denseVectorIndexing) {
		this.denseVectorIndexing = denseVectorIndexing;
	}

	public String getSimilarity() {
		return similarity;
	}

	public void setSimilarity(String similarity) {
		this.similarity = similarity;
	}

}
