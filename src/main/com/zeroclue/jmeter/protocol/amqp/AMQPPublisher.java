package com.zeroclue.jmeter.protocol.amqp;

import com.rabbitmq.client.AMQP;

import java.io.IOException;
import java.security.*;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.Interruptible;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.property.TestElementProperty;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

import com.rabbitmq.client.Channel;


/**
 * JMeter creates an instance of a sampler class for every occurrence of the
 * element in every thread. [some additional copies may be created before the
 * test run starts]
 *
 * Thus each sampler is guaranteed to be called by a single thread - there is no
 * need to synchronize access to instance variables.
 *
 * However, access to class fields must be synchronized.
 */
public class AMQPPublisher extends AMQPSampler implements Interruptible {

    private static final long serialVersionUID = -8420658040465788497L;

    private static final Logger log = LoggingManager.getLoggerForClass();

    //++ These are JMX names, and must not be changed
    private final static String MESSAGE             = "AMQPPublisher.Message";
    private final static String MESSAGE_ROUTING_KEY = "AMQPPublisher.MessageRoutingKey";
    private final static String MESSAGE_TYPE        = "AMQPPublisher.MessageType";
    private final static String REPLY_TO_QUEUE      = "AMQPPublisher.ReplyToQueue";
    private final static String CONTENT_TYPE        = "AMQPPublisher.ContentType";
    private final static String CORRELATION_ID      = "AMQPPublisher.CorrelationId";
    private static final String CONTENT_ENCODING    = "AMQPPublisher.ContentEncoding";
    private final static String MESSAGE_ID          = "AMQPPublisher.MessageId";
    private final static String MESSAGE_PRIORITY    = "AMQPPublisher.MessagePriority";
    private final static String HEADERS             = "AMQPPublisher.Headers";

    public static boolean DEFAULT_PERSISTENT        = false;
    private final static String PERSISTENT          = "AMQPConsumer.Persistent";

    public static boolean DEFAULT_USE_TX            = false;
    private final static String USE_TX              = "AMQPConsumer.UseTx";

    public static final int DEFAULT_MESSAGE_PRIORITY = 0;
    public static final String DEFAULT_RESPONSE_CODE = "500";

    private transient Channel channel;

    public AMQPPublisher() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SampleResult sample(Entry e) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setSuccessful(false);
        result.setResponseCode(DEFAULT_RESPONSE_CODE);

        try {
            initChannel();
        } catch (Exception ex) {
            log.error("Failed to initialize channel : ", ex);
            result.setResponseMessage(ex.toString());
            return result;
        }

        String data = getMessage();     // sampler data

        result.setSampleLabel(getTitle());

        /*
         * Perform the sampling
         */

        // aggregate samples
        int loop = getIterationsAsInt();
        result.sampleStart();   // start timing

        try {
            AMQP.BasicProperties messageProperties = getProperties();
            byte[] messageBytes = getMessageBytes();

            for (int idx = 0; idx < loop; idx++) {
                // try to force jms semantics.
                // but this does not work since RabbitMQ does not sync to disk if consumers are connected as
                // seen by iostat -cd 1. TPS value remains at 0.

                channel.basicPublish(getExchange(), getMessageRoutingKey(), messageProperties, messageBytes);
                //System.out.println(" [x] Sent message: '" + data + "'");
                //System.out.println(" [x] Message properties: '" + messageProperties.toString() + "'");
            }

            // commit the sample
            if (getUseTx()) {
                channel.txCommit();
            }

            /*
             * Set up the sample result details
             */

            result.setSamplerData(data);
            //result.setResponseData(new String(messageBytes), null);
            result.setResponseData("OK", null);
            result.setDataType(SampleResult.TEXT);

            result.setRequestHeaders(formatHeaders());

            result.setResponseCodeOK();
            result.setResponseMessage("OK");
            result.setSuccessful(true);
        } catch (Exception ex) {
            log.debug(ex.getMessage(), ex);
            result.setResponseCode("000");
            result.setResponseMessage(ex.toString());
        } finally {
            result.sampleEnd();     // end timing
        }

        return result;
    }

    private byte[] getMessageBytes() {
        return getMessage().getBytes();
    }

    /**
     * @return the message routing key for the sample
     */
    public String getMessageRoutingKey() {
        return getPropertyAsString(MESSAGE_ROUTING_KEY);
    }

    public void setMessageRoutingKey(String content) {
        setProperty(MESSAGE_ROUTING_KEY, content);
    }

    /**
     * @return the message for the sample
     */
    public String getMessage() {
        return getPropertyAsString(MESSAGE);
    }

    public void setMessage(String content) {
        setProperty(MESSAGE, content);
    }

    /**
     * @return the message type for the sample
     */
    public String getMessageType() {
        return getPropertyAsString(MESSAGE_TYPE);
    }

    public void setMessageType(String content) {
        setProperty(MESSAGE_TYPE, content);
    }

    /**
     * @return the reply-to queue for the sample
     */
    public String getReplyToQueue() {
        return getPropertyAsString(REPLY_TO_QUEUE);
    }

    public void setReplyToQueue(String content) {
        setProperty(REPLY_TO_QUEUE, content);
    }

    public String getContentType() {
    	return getPropertyAsString(CONTENT_TYPE);
    }
    
    public void setContentType(String contentType) {
    	setProperty(CONTENT_TYPE, contentType);
    }

    public void setContentEncoding(String contentEncoding) {
        setProperty(CONTENT_ENCODING, contentEncoding);
    }

    public String getContentEncoding() {
        return getPropertyAsString(CONTENT_ENCODING);
    }

    /**
     * @return the correlation identifier for the sample
     */
    public String getCorrelationId() {
        return getPropertyAsString(CORRELATION_ID);
    }

    public void setCorrelationId(String content) {
        setProperty(CORRELATION_ID, content);
    }

    /**
     * @return the message id for the sample
     */
    public String getMessageId() {
        return getPropertyAsString(MESSAGE_ID);
    }

    public void setMessageId(String content) {
        setProperty(MESSAGE_ID, content);
    }

    /**
     * @return the message priority for the sample
     */
    public String getMessagePriority() {
        return getPropertyAsString(MESSAGE_PRIORITY);
    }

    public void setMessagePriority(String content) {
        setProperty(MESSAGE_PRIORITY, content);
    }

    public int getMessagePriorityAsInt() {
        return getPropertyAsInt(MESSAGE_PRIORITY);
    }

    public Arguments getHeaders() {
        return (Arguments) getProperty(HEADERS).getObjectValue();
    }

    public void setHeaders(Arguments headers) {
        setProperty(new TestElementProperty(HEADERS, headers));
    }

    public Boolean getPersistent() {
        return getPropertyAsBoolean(PERSISTENT, DEFAULT_PERSISTENT);
    }

    public void setPersistent(Boolean persistent) {
        setProperty(PERSISTENT, persistent);
    }

    public Boolean getUseTx() {
        return getPropertyAsBoolean(USE_TX, DEFAULT_USE_TX);
    }

    public void setUseTx(Boolean tx) {
        setProperty(USE_TX, tx);
    }

    @Override
    public boolean interrupt() {
        cleanup();
        return true;
    }

    @Override
    protected Channel getChannel() {
        return channel;
    }

    @Override
    protected void setChannel(Channel channel) {
        this.channel = channel;
    }

    protected AMQP.BasicProperties getProperties() {
        final AMQP.BasicProperties.Builder builder = new AMQP.BasicProperties.Builder();
        final int deliveryMode = getPersistent() ? 2 : 1;
        final String contentType = StringUtils.defaultIfEmpty(getContentType(), "text/plain");
        
        builder.contentType(contentType)
            .contentEncoding(getContentEncoding())
            .deliveryMode(deliveryMode)
            .correlationId(getCorrelationId())
            .replyTo(getReplyToQueue())
            .type(getMessageType())
            .headers(prepareHeaders());

        if (getMessageId() != null && !getMessageId().isEmpty()) {
            builder.messageId(getMessageId());
        }

        if (getMessagePriority() != null && !getMessagePriority().isEmpty()) {
            builder.priority(getMessagePriorityAsInt());
        } else {
            builder.priority(DEFAULT_MESSAGE_PRIORITY);
        }

        return builder.build();
    }

    protected boolean initChannel() throws IOException, NoSuchAlgorithmException, KeyManagementException {
        boolean ret = super.initChannel();

        if (getUseTx()) {
            channel.txSelect();
        }

        return ret;
    }

    private Map<String, Object> prepareHeaders() {
        Map<String, Object> result = new HashMap<String, Object>();
        Map<String, String> source = getHeaders().getArgumentsAsMap();

        for (Map.Entry<String, String> item : source.entrySet()) {
            result.put(item.getKey(), item.getValue());
        }

        return result;
    }

    private String formatHeaders() {
        Map<String, String> headers = getHeaders().getArgumentsAsMap();
        StringBuilder sb = new StringBuilder();

        for (String key : headers.keySet()) {
            sb.append(key).append(": ").append(headers.get(key)).append("\n");
        }

        return sb.toString();
    }
}
