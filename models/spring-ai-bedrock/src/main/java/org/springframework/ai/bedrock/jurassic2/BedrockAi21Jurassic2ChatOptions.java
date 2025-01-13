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

package org.springframework.ai.bedrock.jurassic2;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Request body for the /complete endpoint of the Jurassic-2 API.
 *
 * @author Ahmed Yousri
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BedrockAi21Jurassic2ChatOptions implements ChatOptions {

	/**
	 * The text which the model is requested to continue.
	 */
	@JsonProperty("prompt")
	private String prompt;

	/**
	 * Number of completions to sample and return.
	 */
	@JsonProperty("numResults")
	private Integer numResults;

	/**
	 * The maximum number of tokens to generate per result.
	 */
	@JsonProperty("maxTokens")
	private Integer maxTokens;

	/**
	 * The minimum number of tokens to generate per result.
	 */
	@JsonProperty("minTokens")
	private Integer minTokens;

	/**
	 * Modifies the distribution from which tokens are sampled.
	 */
	@JsonProperty("temperature")
	private Double temperature;

	/**
	 * Sample tokens from the corresponding top percentile of probability mass.
	 */
	@JsonProperty("topP")
	private Double topP;

	/**
	 * Return the top-K (topKReturn) alternative tokens.
	 */
	@JsonProperty("topKReturn")
	private Integer topK;

	/**
	 * Stops decoding if any of the strings is generated.
	 */
	@JsonProperty("stopSequences")
	private List<String> stopSequences;

	/**
	 * Penalty object for frequency.
	 */
	@JsonProperty("frequencyPenalty")
	private Penalty frequencyPenaltyOptions;

	/**
	 * Penalty object for presence.
	 */
	@JsonProperty("presencePenalty")
	private Penalty presencePenaltyOptions;

	/**
	 * Penalty object for count.
	 */
	@JsonProperty("countPenalty")
	private Penalty countPenaltyOptions;

	// Getters and setters

	public static Builder builder() {
		return new Builder();
	}

	public static BedrockAi21Jurassic2ChatOptions fromOptions(BedrockAi21Jurassic2ChatOptions fromOptions) {
		return builder().prompt(fromOptions.getPrompt())
			.numResults(fromOptions.getNumResults())
			.maxTokens(fromOptions.getMaxTokens())
			.minTokens(fromOptions.getMinTokens())
			.temperature(fromOptions.getTemperature())
			.topP(fromOptions.getTopP())
			.topK(fromOptions.getTopK())
			.stopSequences(fromOptions.getStopSequences())
			.frequencyPenaltyOptions(fromOptions.getFrequencyPenaltyOptions())
			.presencePenaltyOptions(fromOptions.getPresencePenaltyOptions())
			.countPenaltyOptions(fromOptions.getCountPenaltyOptions())
			.build();
	}

	/**
	 * Gets the prompt text for the model to continue.
	 * @return The prompt text.
	 */
	public String getPrompt() {
		return this.prompt;
	}

	/**
	 * Sets the prompt text for the model to continue.
	 * @param prompt The prompt text.
	 */
	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	/**
	 * Gets the number of completions to sample and return.
	 * @return The number of results.
	 */
	public Integer getNumResults() {
		return this.numResults;
	}

	/**
	 * Sets the number of completions to sample and return.
	 * @param numResults The number of results.
	 */
	public void setNumResults(Integer numResults) {
		this.numResults = numResults;
	}

	/**
	 * Gets the maximum number of tokens to generate per result.
	 * @return The maximum number of tokens.
	 */
	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	/**
	 * Sets the maximum number of tokens to generate per result.
	 * @param maxTokens The maximum number of tokens.
	 */
	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	/**
	 * Gets the minimum number of tokens to generate per result.
	 * @return The minimum number of tokens.
	 */
	public Integer getMinTokens() {
		return this.minTokens;
	}

	/**
	 * Sets the minimum number of tokens to generate per result.
	 * @param minTokens The minimum number of tokens.
	 */
	public void setMinTokens(Integer minTokens) {
		this.minTokens = minTokens;
	}

	/**
	 * Gets the temperature for modifying the token sampling distribution.
	 * @return The temperature.
	 */
	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	/**
	 * Sets the temperature for modifying the token sampling distribution.
	 * @param temperature The temperature.
	 */
	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	/**
	 * Gets the topP parameter for sampling tokens from the top percentile of probability
	 * mass.
	 * @return The topP parameter.
	 */
	@Override
	public Double getTopP() {
		return this.topP;
	}

	/**
	 * Sets the topP parameter for sampling tokens from the top percentile of probability
	 * mass.
	 * @param topP The topP parameter.
	 */
	public void setTopP(Double topP) {
		this.topP = topP;
	}

	/**
	 * Gets the top-K (topKReturn) alternative tokens to return.
	 * @return The top-K parameter. (topKReturn)
	 */
	@Override
	public Integer getTopK() {
		return this.topK;
	}

	/**
	 * Sets the top-K (topKReturn) alternative tokens to return.
	 * @param topK The top-K parameter (topKReturn).
	 */
	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	/**
	 * Gets the stop sequences for stopping decoding if any of the strings is generated.
	 * @return The stop sequences.
	 */
	@Override
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	/**
	 * Sets the stop sequences for stopping decoding if any of the strings is generated.
	 * @param stopSequences The stop sequences.
	 */
	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	@JsonIgnore
	public Double getFrequencyPenalty() {
		return getFrequencyPenaltyOptions() != null ? getFrequencyPenaltyOptions().scale() : null;
	}

	@JsonIgnore
	public void setFrequencyPenalty(Double frequencyPenalty) {
		if (frequencyPenalty != null) {
			setFrequencyPenaltyOptions(Penalty.builder().scale(frequencyPenalty).build());
		}
	}

	/**
	 * Gets the frequency penalty object.
	 * @return The frequency penalty object.
	 */
	public Penalty getFrequencyPenaltyOptions() {
		return this.frequencyPenaltyOptions;
	}

	/**
	 * Sets the frequency penalty object.
	 * @param frequencyPenaltyOptions The frequency penalty object.
	 */
	public void setFrequencyPenaltyOptions(Penalty frequencyPenaltyOptions) {
		this.frequencyPenaltyOptions = frequencyPenaltyOptions;
	}

	@Override
	@JsonIgnore
	public Double getPresencePenalty() {
		return getPresencePenaltyOptions() != null ? getPresencePenaltyOptions().scale() : null;
	}

	@JsonIgnore
	public void setPresencePenalty(Double presencePenalty) {
		if (presencePenalty != null) {
			setPresencePenaltyOptions(Penalty.builder().scale(presencePenalty).build());
		}
	}

	/**
	 * Gets the presence penalty object.
	 * @return The presence penalty object.
	 */
	public Penalty getPresencePenaltyOptions() {
		return this.presencePenaltyOptions;
	}

	/**
	 * Sets the presence penalty object.
	 * @param presencePenaltyOptions The presence penalty object.
	 */
	public void setPresencePenaltyOptions(Penalty presencePenaltyOptions) {
		this.presencePenaltyOptions = presencePenaltyOptions;
	}

	/**
	 * Gets the count penalty object.
	 * @return The count penalty object.
	 */
	public Penalty getCountPenaltyOptions() {
		return this.countPenaltyOptions;
	}

	/**
	 * Sets the count penalty object.
	 * @param countPenaltyOptions The count penalty object.
	 */
	public void setCountPenaltyOptions(Penalty countPenaltyOptions) {
		this.countPenaltyOptions = countPenaltyOptions;
	}

	@Override
	@JsonIgnore
	public String getModel() {
		return null;
	}

	@Override
	public BedrockAi21Jurassic2ChatOptions copy() {
		return fromOptions(this);
	}

	public static class Builder {

		private final BedrockAi21Jurassic2ChatOptions request = new BedrockAi21Jurassic2ChatOptions();

		public Builder prompt(String prompt) {
			this.request.setPrompt(prompt);
			return this;
		}

		public Builder numResults(Integer numResults) {
			this.request.setNumResults(numResults);
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.request.setMaxTokens(maxTokens);
			return this;
		}

		public Builder minTokens(Integer minTokens) {
			this.request.setMinTokens(minTokens);
			return this;
		}

		public Builder temperature(Double temperature) {
			this.request.setTemperature(temperature);
			return this;
		}

		public Builder topP(Double topP) {
			this.request.setTopP(topP);
			return this;
		}

		public Builder stopSequences(List<String> stopSequences) {
			this.request.setStopSequences(stopSequences);
			return this;
		}

		public Builder topK(Integer topKReturn) {
			this.request.setTopK(topKReturn);
			return this;
		}

		public Builder frequencyPenaltyOptions(BedrockAi21Jurassic2ChatOptions.Penalty frequencyPenalty) {
			this.request.setFrequencyPenaltyOptions(frequencyPenalty);
			return this;
		}

		public Builder presencePenaltyOptions(BedrockAi21Jurassic2ChatOptions.Penalty presencePenalty) {
			this.request.setPresencePenaltyOptions(presencePenalty);
			return this;
		}

		public Builder countPenaltyOptions(BedrockAi21Jurassic2ChatOptions.Penalty countPenalty) {
			this.request.setCountPenaltyOptions(countPenalty);
			return this;
		}

		public BedrockAi21Jurassic2ChatOptions build() {
			return this.request;
		}

	}

	/**
	 * Penalty object for frequency, presence, and count penalties.
	 *
	 * @param scale The scale of the penalty.
	 * @param applyToNumbers Whether to apply the penalty to numbers.
	 * @param applyToPunctuations Whether to apply the penalty to punctuations.
	 * @param applyToStopwords Whether to apply the penalty to stopwords.
	 * @param applyToWhitespaces Whether to apply the penalty to whitespaces.
	 * @param applyToEmojis Whether to apply the penalty to emojis.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Penalty(@JsonProperty("scale") Double scale, @JsonProperty("applyToNumbers") Boolean applyToNumbers,
			@JsonProperty("applyToPunctuations") Boolean applyToPunctuations,
			@JsonProperty("applyToStopwords") Boolean applyToStopwords,
			@JsonProperty("applyToWhitespaces") Boolean applyToWhitespaces,
			@JsonProperty("applyToEmojis") Boolean applyToEmojis) {

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private Double scale;

			// can't keep it null due to modelOptionsUtils#mapToClass convert null to
			// false
			private Boolean applyToNumbers = true;

			private Boolean applyToPunctuations = true;

			private Boolean applyToStopwords = true;

			private Boolean applyToWhitespaces = true;

			private Boolean applyToEmojis = true;

			public Builder scale(Double scale) {
				this.scale = scale;
				return this;
			}

			public Builder applyToNumbers(Boolean applyToNumbers) {
				this.applyToNumbers = applyToNumbers;
				return this;
			}

			public Builder applyToPunctuations(Boolean applyToPunctuations) {
				this.applyToPunctuations = applyToPunctuations;
				return this;
			}

			public Builder applyToStopwords(Boolean applyToStopwords) {
				this.applyToStopwords = applyToStopwords;
				return this;
			}

			public Builder applyToWhitespaces(Boolean applyToWhitespaces) {
				this.applyToWhitespaces = applyToWhitespaces;
				return this;
			}

			public Builder applyToEmojis(Boolean applyToEmojis) {
				this.applyToEmojis = applyToEmojis;
				return this;
			}

			public Penalty build() {
				return new Penalty(this.scale, this.applyToNumbers, this.applyToPunctuations, this.applyToStopwords,
						this.applyToWhitespaces, this.applyToEmojis);
			}

		}

	}

}
