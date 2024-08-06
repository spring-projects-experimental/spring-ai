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
package org.springframework.ai.image.observation;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.model.observation.ModelObservationContext;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.util.Assert;

/**
 * Context used to store metadata for image model exchanges.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ImageModelObservationContext extends ModelObservationContext<ImagePrompt, ImageResponse> {

	private final ImageModelRequestOptions requestOptions;

	ImageModelObservationContext(ImagePrompt imagePrompt, AiOperationMetadata operationMetadata,
			ImageModelRequestOptions requestOptions) {
		super(imagePrompt, operationMetadata);
		Assert.notNull(requestOptions, "requestOptions cannot be null");
		this.requestOptions = requestOptions;
	}

	public ImageModelRequestOptions getRequestOptions() {
		return requestOptions;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private ImagePrompt imagePrompt;

		private AiOperationMetadata operationMetadata;

		private ImageModelRequestOptions requestOptions;

		private Builder() {
		}

		public Builder imagePrompt(ImagePrompt imagePrompt) {
			this.imagePrompt = imagePrompt;
			return this;
		}

		public Builder operationMetadata(AiOperationMetadata operationMetadata) {
			this.operationMetadata = operationMetadata;
			return this;
		}

		public Builder requestOptions(ImageModelRequestOptions requestOptions) {
			this.requestOptions = requestOptions;
			return this;
		}

		public ImageModelObservationContext build() {
			return new ImageModelObservationContext(imagePrompt, operationMetadata, requestOptions);
		}

	}

}
