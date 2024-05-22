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
package org.springframework.ai.stabilityai.image;

import org.springframework.ai.image.AbstractImage;

/**
 * Represents an image Jpeg format used within the Stability AI context. This class
 * extends {@link AbstractImage} with a specific focus on images that are represented as
 * Bytes of Jpeg. It is particularly useful for handling image data that is transmitted
 * over networks where binary data needs to be encoded as text.
 * <p>
 * Instances of this class are associated with the {@link StabilityAiImageType#JPEG} image
 * type, indicating that the image data is in Jpeg format.
 * </p>
 *
 * @author youngmon
 * @version 0.8.1
 */
public class StabilityJpegImage extends AbstractImage<String> {

	StabilityJpegImage(final String data) {
		super(data, StabilityAiImageType.JPEG);
	}

}
