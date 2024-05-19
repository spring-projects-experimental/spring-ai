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
package org.springframework.ai.chat.messages;

import java.util.Map;

/**
 * The FunctionMessage class represents a message with a function content in a chat
 * application.
 */
public class FunctionMessage extends AbstractMessage {

	public FunctionMessage(String content) {
		super(MessageType.FUNCTION, content);
	}

	public FunctionMessage(String content, Map<String, Object> properties) {
		super(MessageType.FUNCTION, content, properties);
	}

	@Override
	public String toString() {
		return "FunctionMessage{" + "content='" + getContent() + '\'' + ", properties=" + metadata + ", messageType="
				+ messageType + '}';
	}

}
