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
package org.springframework.ai.autoconfigure.vectorstore.oracle;

import org.springframework.ai.autoconfigure.CommonVectorStoreProperties;
import org.springframework.ai.vectorstore.OracleVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static org.springframework.ai.vectorstore.OracleVectorStore.DEFAULT_SEARCH_ACCURACY;

/**
 * @author Loïc Lefèvre
 */
@ConfigurationProperties(OracleVectorStoreProperties.CONFIG_PREFIX)
public class OracleVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.oracle";

	private String tableName = OracleVectorStore.DEFAULT_TABLE_NAME;

	private OracleVectorStore.OracleVectorStoreIndexType indexType = OracleVectorStore.DEFAULT_INDEX_TYPE;

	private OracleVectorStore.OracleVectorStoreDistanceType distanceType = OracleVectorStore.DEFAULT_DISTANCE_TYPE;

	private int dimensions = OracleVectorStore.DEFAULT_DIMENSIONS;

	private boolean removeExistingVectorStoreTable;

	private boolean forcedNormalization;

	private int searchAccuracy = DEFAULT_SEARCH_ACCURACY;

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public OracleVectorStore.OracleVectorStoreIndexType getIndexType() {
		return indexType;
	}

	public void setIndexType(OracleVectorStore.OracleVectorStoreIndexType indexType) {
		this.indexType = indexType;
	}

	public OracleVectorStore.OracleVectorStoreDistanceType getDistanceType() {
		return distanceType;
	}

	public void setDistanceType(OracleVectorStore.OracleVectorStoreDistanceType distanceType) {
		this.distanceType = distanceType;
	}

	public int getDimensions() {
		return dimensions;
	}

	public void setDimensions(int dimensions) {
		this.dimensions = dimensions;
	}

	public boolean isRemoveExistingVectorStoreTable() {
		return removeExistingVectorStoreTable;
	}

	public void setRemoveExistingVectorStoreTable(boolean removeExistingVectorStoreTable) {
		this.removeExistingVectorStoreTable = removeExistingVectorStoreTable;
	}

	public boolean isForcedNormalization() {
		return forcedNormalization;
	}

	public void setForcedNormalization(boolean forcedNormalization) {
		this.forcedNormalization = forcedNormalization;
	}

	public int getSearchAccuracy() {
		return searchAccuracy;
	}

	public void setSearchAccuracy(int searchAccuracy) {
		this.searchAccuracy = searchAccuracy;
	}

}
