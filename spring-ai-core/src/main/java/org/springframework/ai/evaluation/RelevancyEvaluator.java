package org.springframework.ai.evaluation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.Content;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RelevancyEvaluator implements Evaluator {

	private static final String DEFAULT_EVALUATION_PROMPT_TEXT = """
			    Your task is to evaluate if the response for the query
			    is in line with the context information provided.\\n
			    You have two options to answer. Either YES/ NO.\\n
			    Answer - YES, if the response for the query
			    is in line with context information otherwise NO.\\n
			    Query: \\n {query}\\n
			    Response: \\n {response}\\n
			    Context: \\n {context}\\n
			    Answer: "
			""";

	private final ChatClient.Builder chatClientBuilder;

	public RelevancyEvaluator(ChatClient.Builder chatClientBuilder) {
		this.chatClientBuilder = chatClientBuilder;
	}

	@Override
	public EvaluationResponse evaluate(EvaluationRequest evaluationRequest) {

		var response = doGetResponse(evaluationRequest);
		var context = doGetSupportingData(evaluationRequest);

		String evaluationResponse = this.chatClientBuilder.build()
			.prompt()
			.user(userSpec -> userSpec.text(DEFAULT_EVALUATION_PROMPT_TEXT)
				.param("query", evaluationRequest.getUserText())
				.param("response", response)
				.param("context", context))
			.call()
			.content();

		boolean passing = false;
		float score = 0;
		if (evaluationResponse.toLowerCase().contains("yes")) {
			passing = true;
			score = 1;
		}

		return new EvaluationResponse(passing, score, "", Collections.emptyMap());
	}

	protected String doGetResponse(EvaluationRequest evaluationRequest) {
		return evaluationRequest.getChatResponse().getResult().getOutput().getContent();
	}

	protected String doGetSupportingData(EvaluationRequest evaluationRequest) {
		List<Content> data = evaluationRequest.getDataList();
		String supportingData = data.stream()
			.filter(node -> node != null && node.getContent() instanceof String)
			.map(node -> (Content) node)
			.map(Content::getContent)
			.collect(Collectors.joining(System.lineSeparator()));
		return supportingData;
	}

}
