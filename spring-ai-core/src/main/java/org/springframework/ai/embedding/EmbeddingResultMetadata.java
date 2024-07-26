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
package org.springframework.ai.embedding;

import org.springframework.ai.model.ResultMetadata;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * @author Christian Tzolov
 */
public class EmbeddingResultMetadata implements ResultMetadata {

	public static EmbeddingResultMetadata EMPTY = new EmbeddingResultMetadata();

	public enum ModalityType {

		TEXT, IMAGE, AUDIO, VIDEO;

	}

	/**
	 * The {@link MimeType} of the source data used to generate the embedding.
	 */
	private final ModalityType modalityType;

	private final String documentId;

	private final MimeType mimeType;

	private final Object documentData;

	public EmbeddingResultMetadata() {
		this("", ModalityType.TEXT, MimeTypeUtils.TEXT_PLAIN, null);
	}

	public EmbeddingResultMetadata(String documentId, ModalityType modalityType, MimeType mimeType,
			Object documentData) {
		Assert.notNull(modalityType, "ModalityType must not be null");
		Assert.notNull(mimeType, "MimeType must not be null");

		this.documentId = documentId;
		this.modalityType = modalityType;
		this.mimeType = mimeType;
		this.documentData = documentData;
	}

	public ModalityType getModalityType() {
		return this.modalityType;
	}

	public MimeType getMimeType() {
		return this.mimeType;
	}

	public String getDocumentId() {
		return this.documentId;
	}

	public Object getDocumentData() {
		return this.documentData;
	}

	public static class ModalityUtils {

		private static MimeType TEXT_MIME_TYPE = MimeTypeUtils.parseMimeType("text/*");

		private static MimeType IMAGE_MIME_TYPE = MimeTypeUtils.parseMimeType("text/*");

		private static MimeType VIDEO_MIME_TYPE = MimeTypeUtils.parseMimeType("video/*");

		private static MimeType AUDIO_MIME_TYPE = MimeTypeUtils.parseMimeType("audio/*");

		/**
		 * Infers the {@link ModalityType} of the source data used to generate the
		 * embedding using the source data {@link MimeType}.
		 * @param mimeType the {@link MimeType} of the source data.
		 * @return Returns the {@link ModalityType} of the source data used to generate
		 * the embedding.
		 */
		public static ModalityType getModalityType(MimeType mimeType) {

			if (mimeType == null) {
				return ModalityType.TEXT;
			}

			if (mimeType.isCompatibleWith(IMAGE_MIME_TYPE)) {
				return ModalityType.IMAGE;
			}
			else if (mimeType.isCompatibleWith(AUDIO_MIME_TYPE)) {
				return ModalityType.AUDIO;
			}
			else if (mimeType.isCompatibleWith(VIDEO_MIME_TYPE)) {
				return ModalityType.VIDEO;
			}
			else if (mimeType.isCompatibleWith(TEXT_MIME_TYPE)) {
				return ModalityType.TEXT;
			}

			throw new IllegalArgumentException("Unsupported MimeType: " + mimeType);
		}

	}

}
