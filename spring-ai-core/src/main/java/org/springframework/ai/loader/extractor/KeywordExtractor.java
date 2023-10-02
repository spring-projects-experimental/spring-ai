/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.loader.extractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.client.AiClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.PromptTemplate;
import org.springframework.util.Assert;

/**
 * Keyword extractor that uses LLM to extract 'excerpt_keywords' metadata field.
 *
 * @author Christian Tzolov
 */
public class KeywordExtractor implements MetadataFeatureExtractor {

	public static final String CONTEXT_STR_PLACEHOLDER = "context_str";

	public static final String KEYWORDS_TEMPLATE = """
			{context_str}. Give %s unique keywords for this
			document. Format as comma separated. Keywords: """;

	/**
	 * LLM predictor
	 */
	private final AiClient aiClient;

	/**
	 * The number of keywords to extract.
	 */
	private final int keywordCount;

	public KeywordExtractor(AiClient aiClient, int keywordCount) {
		Assert.notNull(aiClient, "AiClient must not be null");
		Assert.isTrue(keywordCount >= 1, "Document count must be >= 1");

		this.aiClient = aiClient;
		this.keywordCount = keywordCount;
	}

	@Override
	public List<Map<String, Object>> extract(List<Document> documents) {

		List<Map<String, Object>> result = new ArrayList<>();
		for (Document document : documents) {

			var template = new PromptTemplate(String.format(KEYWORDS_TEMPLATE, keywordCount));
			Prompt prompt = template.create(Map.of(CONTEXT_STR_PLACEHOLDER, document.getContent()));
			String keywords = this.aiClient.generate(prompt).getGeneration().getText();
			result.add(Map.of("excerpt_keywords", keywords));
		}

		return result;
	}

}
