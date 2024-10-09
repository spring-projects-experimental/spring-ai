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
package org.springframework.ai.audio.speech;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SpeechMessage}.
 *
 * @author Thomas Vitale
 */
class SpeechMessageTests {

	@Test
	void whenBuildSpeechMessage() {
		var text = "They're taking the hobbits to Isengard!";
		var speechMessage = new SpeechMessage(text);
		assertThat(speechMessage.getText()).isEqualTo(text);
	}

	@Test
	void whenTextIsNullThenThrow() {
		assertThatThrownBy(() -> new SpeechMessage(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("text cannot be null or empty");
	}

	@Test
	void whenTextIsEmptyThenThrow() {
		assertThatThrownBy(() -> new SpeechMessage("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("text cannot be null or empty");
	}

}