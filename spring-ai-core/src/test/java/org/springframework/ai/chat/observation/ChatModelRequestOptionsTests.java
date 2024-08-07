/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.ai.chat.observation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ChatModelRequestOptions}.
 *
 * @author Thomas Vitale
 */
class ChatModelRequestOptionsTests {

	@Test
	void whenMandatoryRequestOptionsThenReturn() {
		var requestOptions = ChatModelRequestOptions.builder().model("rowena").build();

		assertThat(requestOptions).isNotNull();
	}

	@Test
	void whenModelIsNullThenThrow() {
		assertThatThrownBy(() -> ChatModelRequestOptions.builder().build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("model cannot be null or empty");
	}

	@Test
	void whenModelIsEmptyThenThrow() {
		assertThatThrownBy(() -> ChatModelRequestOptions.builder().model("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("model cannot be null or empty");
	}

}
