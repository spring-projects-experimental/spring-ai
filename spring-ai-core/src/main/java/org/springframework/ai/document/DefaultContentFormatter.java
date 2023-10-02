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

package org.springframework.ai.document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 */
public class DefaultContentFormatter implements ContentFormatter {

	private static final String TEMPLATE_CONTENT_PLACEHOLDER = "{content}";

	private static final String TEMPLATE_METADATA_STRING_PLACEHOLDER = "{metadata_string}";

	private static final String TEMPLATE_VALUE_PLACEHOLDER = "{value}";

	private static final String TEMPLATE_KEY_PLACEHOLDER = "{key}";

	private static final String DEFAULT_METADATA_TEMPLATE = String.format("%s: %s", TEMPLATE_KEY_PLACEHOLDER,
			TEMPLATE_VALUE_PLACEHOLDER);

	private static final String DEFAULT_METADATA_SEPARATOR = "\n";

	private static final String DEFAULT_TEXT_TEMPLATE = String.format("%s\n\n%s", TEMPLATE_METADATA_STRING_PLACEHOLDER,
			TEMPLATE_CONTENT_PLACEHOLDER);

	/**
	 * Template for how metadata is formatted, with {key} and {value} placeholders.
	 */
	private final String metadataTemplate;

	/**
	 * Separator between metadata fields when converting to string.
	 */
	private final String metadataSeparator;

	/**
	 * Template for how Document text is formatted, with {content} and {metadata_string}
	 * placeholders.
	 */
	private final String textTemplate;

	/**
	 * Metadata keys that are excluded from text for the LLM.
	 */
	private final List<String> excludedLlmMetadataKeys;

	/**
	 * Metadata keys that are excluded from text for the embed model.
	 */
	private final List<String> excludedEmbedMetadataKeys;

	/**
	 * Start building a new configuration.
	 * @return The entry point for creating a new configuration.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * {@return the default config}
	 */
	public static DefaultContentFormatter defaultConfig() {

		return builder().build();
	}

	private DefaultContentFormatter(Builder builder) {
		this.metadataTemplate = builder.metadataTemplate;
		this.metadataSeparator = builder.metadataSeparator;
		this.textTemplate = builder.textTemplate;
		this.excludedLlmMetadataKeys = builder.excludedLlmMetadataKeys;
		this.excludedEmbedMetadataKeys = builder.excludedEmbedMetadataKeys;
	}

	public static class Builder {

		private String metadataTemplate = DEFAULT_METADATA_TEMPLATE;

		private String metadataSeparator = DEFAULT_METADATA_SEPARATOR;

		private String textTemplate = DEFAULT_TEXT_TEMPLATE;

		private List<String> excludedLlmMetadataKeys = new ArrayList<>();

		private List<String> excludedEmbedMetadataKeys = new ArrayList<>();

		private Builder() {
		}

		public Builder from(DefaultContentFormatter fromFormatter) {
			this.withExcludedEmbedMetadataKeys(fromFormatter.getExcludedEmbedMetadataKeys())
				.withExcludedLlmMetadataKeys(fromFormatter.getExcludedLlmMetadataKeys())
				.withMetadataSeparator(fromFormatter.getMetadataSeparator())
				.withMetadataTemplate(fromFormatter.getMetadataTemplate())
				.withTextTemplate(fromFormatter.getTextTemplate());
			return this;
		}

		/**
		 * Configures the Document metadata template.
		 * @param metadataTemplate Metadata template to use.
		 * @return this builder
		 */
		public Builder withMetadataTemplate(String metadataTemplate) {
			Assert.hasText(metadataTemplate, "Metadata Template must not be empty");
			this.metadataTemplate = metadataTemplate;
			return this;
		}

		/**
		 * Configures the Document metadata separator.
		 * @param metadataSeparator Metadata separator to use.
		 * @return this builder
		 */
		public Builder withMetadataSeparator(String metadataSeparator) {
			Assert.notNull(metadataSeparator, "Metadata separator must not be empty");
			this.metadataSeparator = metadataSeparator;
			return this;
		}

		/**
		 * Configures the Document text template.
		 * @param textTemplate Document's content template.
		 * @return this builder
		 */
		public Builder withTextTemplate(String textTemplate) {
			Assert.hasText(textTemplate, "Document's text template must not be empty");
			this.textTemplate = textTemplate;
			return this;
		}

		/**
		 * Configures the excluded LLM metadata keys to filter out from the model.
		 * @param excludedLlmMetadataKeys Excluded LLM metadata keys to use.
		 * @return this builder
		 */
		public Builder withExcludedLlmMetadataKeys(List<String> excludedLlmMetadataKeys) {
			Assert.notNull(excludedLlmMetadataKeys, "Excluded LLM metadata keys must not be null");
			this.excludedLlmMetadataKeys = excludedLlmMetadataKeys;
			return this;
		}

		public Builder withExcludedLlmMetadataKeys(String... keys) {
			Assert.notNull(keys, "Excluded LLM metadata keys must not be null");
			this.excludedLlmMetadataKeys.addAll(Arrays.asList(keys));
			return this;
		}

		/**
		 * Configures the excluded Embed metadata keys to filter out from the model.
		 * @param excludedEmbedMetadataKeys Excluded Embed metadata keys to use.
		 * @return this builder
		 */
		public Builder withExcludedEmbedMetadataKeys(List<String> excludedEmbedMetadataKeys) {
			Assert.notNull(excludedEmbedMetadataKeys, "Excluded Embed metadata keys must not be null");
			this.excludedEmbedMetadataKeys = excludedEmbedMetadataKeys;
			return this;
		}

		public Builder withExcludedEmbedMetadataKeys(String... keys) {
			Assert.notNull(keys, "Excluded Embed metadata keys must not be null");
			this.excludedEmbedMetadataKeys.addAll(Arrays.asList(keys));
			return this;
		}

		/**
		 * {@return the immutable configuration}
		 */
		public DefaultContentFormatter build() {
			return new DefaultContentFormatter(this);
		}

	}

	@Override
	public String format(Document document, MetadataMode metadataMode) {

		var metadata = metadataFilter(document.getMetadata(), metadataMode);

		var metadataText = metadata.entrySet()
			.stream()
			.map(metadataEntry -> this.metadataTemplate.replace(TEMPLATE_KEY_PLACEHOLDER, metadataEntry.getKey())
				.replace(TEMPLATE_VALUE_PLACEHOLDER, metadataEntry.getValue().toString()))
			.collect(Collectors.joining(this.metadataSeparator));

		return this.textTemplate.replace(TEMPLATE_METADATA_STRING_PLACEHOLDER, metadataText)
			.replace(TEMPLATE_CONTENT_PLACEHOLDER, document.getContent());
	}

	/**
	 * Filters the metadata by the configured MetadataMode.
	 * @param metadata Document metadata.
	 * @return Returns the filtered by configured mode metadata.
	 */
	protected Map<String, Object> metadataFilter(Map<String, Object> metadata, MetadataMode metadataMode) {

		if (metadataMode == ContentFormatter.MetadataMode.ALL) {
			return new HashMap<String, Object>(metadata);
		}
		if (metadataMode == ContentFormatter.MetadataMode.NONE) {
			return new HashMap<String, Object>(Collections.emptyMap());
		}

		Set<String> usableMetadataKeys = new HashSet<>(metadata.keySet());

		if (metadataMode == ContentFormatter.MetadataMode.LLM) {
			usableMetadataKeys.removeAll(this.excludedLlmMetadataKeys);
		}
		else if (metadataMode == ContentFormatter.MetadataMode.EMBED) {
			usableMetadataKeys.removeAll(this.excludedEmbedMetadataKeys);
		}

		return new HashMap<String, Object>(metadata.entrySet()
			.stream()
			.filter(e -> usableMetadataKeys.contains(e.getKey()))
			.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())));
	}

	public String getMetadataTemplate() {
		return this.metadataTemplate;
	}

	public String getMetadataSeparator() {
		return this.metadataSeparator;
	}

	public String getTextTemplate() {
		return this.textTemplate;
	}

	public List<String> getExcludedLlmMetadataKeys() {
		return Collections.unmodifiableList(this.excludedLlmMetadataKeys);
	}

	public List<String> getExcludedEmbedMetadataKeys() {
		return Collections.unmodifiableList(this.excludedEmbedMetadataKeys);
	}

}
