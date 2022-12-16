package com.spredfast.kafka.connect.gcs;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.spredfast.kafka.connect.gcs.source.*;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.connect.source.SourceTaskContext;
import org.apache.kafka.connect.storage.OffsetStorageReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GCSSourceTaskTest {

	GCSFilesReader.PartitionFilter partitionFilter;

	void setUp(Map<String, String> taskConfig) throws Exception {
		SourceTaskContext context = new MockSourceTaskContext();
		GCSSourceTask task = new GCSSourceTask();
		task.initialize(context);
		task.start(taskConfig);
		GCSSourceConfig gcsSourceConfig = task.gcsSourceConfig;
		partitionFilter = gcsSourceConfig.partitionFilter;
	}

	Map<String, String> overrideConfig(Map<String, String> configOverrides) {
		Map<String, String> taskConfig = new HashMap<>();
		taskConfig.put("gcs.bucket", "dummyGCSBucket");
		taskConfig.put("partitions", "0,1,2");
		taskConfig.put("topic", "");
		for (String key : configOverrides.keySet()) {
			taskConfig.put(key, configOverrides.get(key));
		}
		return taskConfig;
	}

	void withConfig(Map<String, String> configOverrides) throws Exception {
		Map<String, String> taskConfig = overrideConfig(configOverrides);
		setUp(taskConfig);
	}


	@Test
	void testBuildConfigPartitionMatch() throws Exception {
		Map<String, String> configOverrides = new HashMap<>();
		configOverrides.put("partitions", "1,999,2");
		withConfig(configOverrides);
		Boolean match1 = partitionFilter.matches("anyTopic", 999);
		assertEquals(true, match1);
		Boolean match2 = partitionFilter.matches("anyTopic", 998);
		assertEquals(false, match2);
	}

	@Test
	void testBuildConfigAllTopicsMatch() throws Exception {
		Map<String, String> configOverrides = new HashMap<>();
		withConfig(configOverrides);
		Boolean match1 = partitionFilter.matches("anyTopic", 1);
		assertEquals(true, match1);
	}

	@Test
	void testBuildConfigSomeTopicsMatch() throws Exception {
		Map<String, String> configOverrides = new HashMap<>();
		configOverrides.put("topics", "topic1,topic2");
		withConfig(configOverrides);
		Boolean match1 = partitionFilter.matches("anyTopic", 1);
		assertEquals(false, match1);
		Boolean match2 = partitionFilter.matches("topic1", 1);
		assertEquals(true, match2);
		Boolean match3 = partitionFilter.matches("topic2", 1);
		assertEquals(true, match3);
	}

	@Test
	void testBuildConfigAllTopicsMatchSomeTopicsIgnored() throws Exception {
		Map<String, String> configOverrides = new HashMap<>();
		configOverrides.put("topics.ignore", "topic1,topic2");
		withConfig(configOverrides);
		Boolean match1 = partitionFilter.matches("anyTopic", 1);
		assertEquals(true, match1);
		Boolean match2 = partitionFilter.matches("topic1", 1);
		assertEquals(false, match2);
		Boolean match3 = partitionFilter.matches("topic2", 1);
		assertEquals(false, match3);
	}

	void checkThatOnlyThisTaskMatches(String topicName, int partition, String taskNumToMatch) throws Exception {
		Map<String, String> taskConfig = new HashMap<>();
		String[] taskNums = {"0", "1", "2", "3", "4"};
		for (int i = 0; i <= taskNums.length - 1; i++) {
			String taskNum = taskNums[i];
			taskConfig.put("taskNum", taskNum);
			taskConfig.put("tasks.splitTopics", "true");
			taskConfig.put("taskCount", "5");
			withConfig(taskConfig);
			assertEquals(partitionFilter.matches(topicName, partition), taskNum.equals(taskNumToMatch) );
		}
	}

	@Test
	void testBuildConfigSplitTopicsAcrossTasks() throws Exception {
		// it doesn't matter much which value for taskNumToMatch happen to work here - should be one between 0 and 4
		// but checkThatOnlyThisTaskMatches checks that it works and that 's the only value working
		// also using different topicName / partition combinations here help seeing that the partitions are roughly balanced
		String topicName = "eevee-banking-1";
		checkThatOnlyThisTaskMatches(topicName,0, "1");

		topicName = "eevee-banking-2";
		checkThatOnlyThisTaskMatches(topicName,0, "0");

		topicName = "eevee-adapter";
		checkThatOnlyThisTaskMatches(topicName,0, "0");

		topicName = "eevee-albatros";
		checkThatOnlyThisTaskMatches(topicName,0, "4");
		checkThatOnlyThisTaskMatches(topicName,1, "2");
		checkThatOnlyThisTaskMatches(topicName,2, "1");
		checkThatOnlyThisTaskMatches(topicName,3, "1");



	}


}
