package io.learnaws.dynamodb;

import java.util.List;

import software.amazon.awssdk.services.dynamodb.model.DescribeStreamResponse;
import software.amazon.awssdk.services.dynamodb.model.GetRecordsResponse;
import software.amazon.awssdk.services.dynamodb.model.OperationType;
import software.amazon.awssdk.services.dynamodb.model.ShardIteratorType;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

/**
 * Manually reads DynamoDB Streams records the way a Lambda event-source mapping would do it
 * for you automatically (that automation is what Module 05 adds). Seeing the raw
 * describe-stream / get-shard-iterator / get-records protocol once is worth it before it's
 * hidden behind Lambda's ESM.
 */
public final class TaskStreamPoller {

    private TaskStreamPoller() {
    }

    /** Returns every INSERT record's new image currently readable from the stream's first shard. */
    public static List<Task> pollNewTasks(DynamoDbStreamsClient streams, String streamArn) {
        DescribeStreamResponse describe = streams.describeStream(b -> b.streamArn(streamArn));
        String shardId = describe.streamDescription().shards().get(0).shardId();

        String shardIterator = streams.getShardIterator(b -> b
                        .streamArn(streamArn)
                        .shardId(shardId)
                        .shardIteratorType(ShardIteratorType.TRIM_HORIZON))
                .shardIterator();

        GetRecordsResponse records = streams.getRecords(b -> b.shardIterator(shardIterator));

        return records.records().stream()
                .filter(r -> r.eventName() == OperationType.INSERT)
                .map(r -> TaskMapper.fromStreamImage(r.dynamodb().newImage()))
                .toList();
    }
}
