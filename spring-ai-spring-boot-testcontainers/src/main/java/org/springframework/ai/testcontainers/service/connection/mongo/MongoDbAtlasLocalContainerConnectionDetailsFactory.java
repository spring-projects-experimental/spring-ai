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
package org.springframework.ai.testcontainers.service.connection.mongo;

import com.mongodb.ConnectionString;
import org.springframework.boot.autoconfigure.mongo.MongoConnectionDetails;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;

/**
 * @author Eddú Meléndez
 */
class MongoDbAtlasLocalContainerConnectionDetailsFactory
		extends ContainerConnectionDetailsFactory<MongoDBAtlasLocalContainer, MongoConnectionDetails> {

	@Override
	protected MongoConnectionDetails getContainerConnectionDetails(
			ContainerConnectionSource<MongoDBAtlasLocalContainer> source) {
		return new MongoDbAtlasLocalContainerConnectionDetails(source);
	}

	/**
	 * {@link MongoConnectionDetails} backed by a {@link ContainerConnectionSource}.
	 */
	private static final class MongoDbAtlasLocalContainerConnectionDetails
			extends ContainerConnectionDetails<MongoDBAtlasLocalContainer> implements MongoConnectionDetails {

		private MongoDbAtlasLocalContainerConnectionDetails(
				ContainerConnectionSource<MongoDBAtlasLocalContainer> source) {
			super(source);
		}

		@Override
		public ConnectionString getConnectionString() {
			return new ConnectionString(getContainer().getConnectionString());
		}

	}

}
