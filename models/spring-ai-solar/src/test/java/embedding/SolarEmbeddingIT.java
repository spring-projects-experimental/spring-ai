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

package embedding;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import chat.SolarTestConfiguration;

/**
 * @author Seunghyeon Ji
 */
@SpringBootTest(classes = SolarTestConfiguration.class)
@EnabledIfEnvironmentVariables({ @EnabledIfEnvironmentVariable(named = "SOLAR_API_KEY", matches = ".+") })
class SolarEmbeddingIT {

	@Autowired
	private EmbeddingModel embeddingModel;

	@Test
	void defaultEmbedding() {
		Assertions.assertThat(this.embeddingModel).isNotNull();

		EmbeddingResponse embeddingResponse = this.embeddingModel.embedForResponse(List.of("Hello World"));

		assertThat(embeddingResponse.getResults()).hasSize(1);

		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(4096);

		Assertions.assertThat(this.embeddingModel.dimensions()).isEqualTo(4096);
	}

	@Test
	void batchEmbedding() {
		Assertions.assertThat(this.embeddingModel).isNotNull();

		EmbeddingResponse embeddingResponse = this.embeddingModel.embedForResponse(List.of("Hello World", "HI"));

		assertThat(embeddingResponse.getResults()).hasSize(2);

		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(4096);

		assertThat(embeddingResponse.getResults().get(1)).isNotNull();
		assertThat(embeddingResponse.getResults().get(1).getOutput()).hasSize(4096);

		Assertions.assertThat(this.embeddingModel.dimensions()).isEqualTo(4096);
	}

}
