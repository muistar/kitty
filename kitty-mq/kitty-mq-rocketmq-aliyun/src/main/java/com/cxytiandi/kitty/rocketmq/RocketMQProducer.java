package com.cxytiandi.kitty.rocketmq;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.SendCallback;
import com.aliyun.openservices.ons.api.SendResult;
import com.aliyun.openservices.ons.api.bean.OrderProducerBean;
import com.aliyun.openservices.ons.api.bean.ProducerBean;
import com.aliyun.openservices.shade.org.apache.commons.lang3.StringUtils;
import com.cxytiandi.kitty.common.cat.CatTransactionManager;
import com.cxytiandi.kitty.common.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * RocketMq 消息发送
 *
 * @作者 尹吉欢
 * @个人微信 jihuan900
 * @微信公众号 猿天地
 * @GitHub https://github.com/yinjihuan
 * @作者介绍 http://cxytiandi.com/about
 * @时间 2020-06-07 16:02
 */
@Slf4j
public class RocketMQProducer {

    private ProducerBean producerBean;
    private OrderProducerBean orderProducerBean;
    private TransactionMQService transactionMQService;

    public RocketMQProducer(ProducerBean producerBean, OrderProducerBean orderProducerBean, TransactionMQService transactionMQService) {
        this.producerBean = producerBean;
        this.orderProducerBean = orderProducerBean;
        this.transactionMQService = transactionMQService;
    }

    public SendResult sendMessage(Message message) {
        return CatTransactionManager.newTransaction(() -> {
            try {
                SendResult result = producerBean.send(message);
                return result;
            } catch (Exception e) {
                log.error("sendMessage error", e);
                sendTransactionMessage(message);
            }
            return new SendResult();
        }, RocketMQConstant.MQ_CAT_TYPE, RocketMQConstant.SEND_MESSAGE);
    }

    public SendResult sendMessage(Message message, boolean isSaveDb) {
        if (isSaveDb) {
            return sendMessage(message);
        }
        return CatTransactionManager.newTransaction(() -> {
            SendResult result = producerBean.send(message);
            return result;
        }, RocketMQConstant.MQ_CAT_TYPE, RocketMQConstant.SEND_MESSAGE);

    }

    public SendResult sendMessage(String topic, String tag, String body) {
        return sendMessage(buildMessage(topic, tag, null, body));
    }

    public <T> SendResult sendMessage(String topic, String tag, Class<T> body) {
        return sendMessage(buildMessage(topic, tag, null, JsonUtils.toJson(body)));
    }

    public SendResult sendMessage(String topic, String tag, String key, String body) {
        return sendMessage(buildMessage(topic, tag, key, body));
    }

    public <T> SendResult sendMessage(String topic, String tag, String key, Class<T> body) {
        return sendMessage(buildMessage(topic, tag, key, JsonUtils.toJson(body)));
    }

    public SendResult sendDelayMessage(Message message, long delayTime, TimeUnit delayTimeUnit) {
        message.setStartDeliverTime(System.currentTimeMillis() + delayTimeUnit.toMinutes(delayTime));
        return sendMessage(message);
    }

    public SendResult sendDelayMessage(String topic, String tag, String body, long delayTime, TimeUnit delayTimeUnit) {
        return sendMessage(buildMessage(topic, tag, null, body, delayTime, delayTimeUnit));
    }

    public <T> SendResult sendDelayMessage(String topic, String tag, Class<T> body, long delayTime, TimeUnit delayTimeUnit) {
        return sendMessage(buildMessage(topic, tag, null, JsonUtils.toJson(body), delayTime, delayTimeUnit));
    }

    public SendResult sendDelayMessage(String topic, String tag, String key, String body, long delayTime, TimeUnit delayTimeUnit) {
        return sendMessage(buildMessage(topic, tag, key, body, delayTime, delayTimeUnit));
    }

    public <T> SendResult sendDelayMessage(String topic, String tag, String key, Class<T> body, long delayTime, TimeUnit delayTimeUnit) {
        return sendMessage(buildMessage(topic, tag, key, JsonUtils.toJson(body), delayTime, delayTimeUnit));
    }

    public SendResult sendOrderMessage(Message message, String shardingKey) {
        return CatTransactionManager.newTransaction(() -> {
            try {
                return orderProducerBean.send(message, shardingKey);
            } catch (Exception e) {
                log.error("sendOrderMessage error", e);
                sendTransactionOrderMessage(message, shardingKey);
            }
            return new SendResult();
        }, RocketMQConstant.MQ_CAT_TYPE, RocketMQConstant.SEND_ORDER_MESSAGE);
    }

    public SendResult sendOrderMessage(Message message, String shardingKey, boolean isSaveDb) {
        if (isSaveDb) {
            return sendOrderMessage(message, shardingKey);
        }

        return CatTransactionManager.newTransaction(() -> {
            return orderProducerBean.send(message, shardingKey);
        }, RocketMQConstant.MQ_CAT_TYPE, RocketMQConstant.SEND_ORDER_MESSAGE);
    }

    public SendResult sendOrderMessage(Message message) {
        return sendOrderMessage(message, message.getShardingKey());
    }

    public SendResult sendOrderMessage(String topic, String tag, String body, String shardingKey) {
        return sendOrderMessage(buildMessage(topic, tag, null, body, shardingKey));
    }

    public <T> SendResult sendOrderMessage(String topic, String tag, Class<T> body, String shardingKey) {
        return sendOrderMessage(buildMessage(topic, tag, null, JsonUtils.toJson(body), shardingKey));
    }

    public SendResult sendOrderMessage(String topic, String tag, String key, String body, String shardingKey) {
        return sendOrderMessage(buildMessage(topic, tag, key, body, shardingKey));
    }

    public <T> SendResult sendOrderMessage(String topic, String tag, String key, Class<T> body, String shardingKey) {
        return sendOrderMessage(buildMessage(topic, tag, key, JsonUtils.toJson(body), shardingKey));
    }

    public void sendTransactionMessage(Message message, RocketMQMessageTypeEnum type) {
        CatTransactionManager.newTransaction(() -> {
            transactionMQService.saveTransactionMQMessage(message, type);
        }, RocketMQConstant.MQ_CAT_TYPE, RocketMQConstant.SEND_TRANSACTION_MESSAGE);
    }

    public void sendTransactionMessage(Message message) {
        CatTransactionManager.newTransaction(() -> {
            transactionMQService.saveTransactionMQMessage(message);
        }, RocketMQConstant.MQ_CAT_TYPE, RocketMQConstant.SEND_TRANSACTION_MESSAGE);
    }

    public void sendTransactionMessage(String topic, String tag, String body) {
        sendTransactionMessage(buildMessage(topic, tag, null, body));
    }

    public <T> void sendTransactionMessage(String topic, String tag, Class<T> body) {
        sendTransactionMessage(buildMessage(topic, tag, null, JsonUtils.toJson(body)));
    }

    public void sendTransactionMessage(String topic, String tag, String key, String body) {
        sendTransactionMessage(buildMessage(topic, tag, key, body));
    }

    public <T> void sendTransactionMessage(String topic, String tag, String key, Class<T> body) {
        sendTransactionMessage(buildMessage(topic, tag, key, JsonUtils.toJson(body)));
    }

    public void sendTransactionDelayMessage(Message message, long delayTime, TimeUnit delayTimeUnit) {
        message.setStartDeliverTime(System.currentTimeMillis() + delayTimeUnit.toMinutes(delayTime));
        sendTransactionMessage(message, RocketMQMessageTypeEnum.DELAY);
    }

    public void sendTransactionDelayMessage(String topic, String tag, String body, long delayTime, TimeUnit delayTimeUnit) {
        sendTransactionMessage(buildMessage(topic, tag, null, body, delayTime, delayTimeUnit), RocketMQMessageTypeEnum.DELAY);
    }

    public <T> void sendTransactionDelayMessage(String topic, String tag, Class<T> body, long delayTime, TimeUnit delayTimeUnit) {
        sendTransactionMessage(buildMessage(topic, tag, null, JsonUtils.toJson(body), delayTime, delayTimeUnit), RocketMQMessageTypeEnum.DELAY);
    }

    public void sendTransactionDelayMessage(String topic, String tag, String key, String body, long delayTime, TimeUnit delayTimeUnit) {
        sendTransactionMessage(buildMessage(topic, tag, key, body, delayTime, delayTimeUnit), RocketMQMessageTypeEnum.DELAY);
    }

    public <T> void sendTransactionDelayMessage(String topic, String tag, String key, Class<T> body, long delayTime, TimeUnit delayTimeUnit) {
        sendTransactionMessage(buildMessage(topic, tag, key, JsonUtils.toJson(body), delayTime, delayTimeUnit), RocketMQMessageTypeEnum.DELAY);
    }

    public void sendTransactionOrderMessage(Message message, String shardingKey) {
        message.setShardingKey(shardingKey);
        sendTransactionMessage(message, RocketMQMessageTypeEnum.ORDER);
    }

    public void sendTransactionOrderMessage(Message message) {
        sendTransactionMessage(message, RocketMQMessageTypeEnum.ORDER);
    }

    public void sendTransactionOrderMessage(String topic, String tag, String body, String shardingKey) {
        sendTransactionMessage(buildMessage(topic, tag, null, body, shardingKey), RocketMQMessageTypeEnum.ORDER);
    }

    public <T> void sendTransactionOrderMessage(String topic, String tag, Class<T> body, String shardingKey) {
        sendTransactionMessage(buildMessage(topic, tag, null, JsonUtils.toJson(body), shardingKey), RocketMQMessageTypeEnum.ORDER);
    }

    public void sendTransactionOrderMessage(String topic, String tag, String key, String body, String shardingKey) {
        sendTransactionMessage(buildMessage(topic, tag, key, body, shardingKey), RocketMQMessageTypeEnum.ORDER);
    }

    public <T> void sendTransactionOrderMessage(String topic, String tag, String key, Class<T> body, String shardingKey) {
        sendTransactionMessage(buildMessage(topic, tag, key, JsonUtils.toJson(body), shardingKey), RocketMQMessageTypeEnum.ORDER);
    }


    private Message buildMessage(String topic, String tag, String key, String body, long delayTime, TimeUnit delayTimeUnit) {
        Message message = new Message();
        message.setTopic(topic);
        message.setTag(tag);
        message.setBody(body.getBytes());
        if (StringUtils.isNotBlank(key)) {
            message.setKey(key);
        }
        if (delayTimeUnit != null && delayTime > 0) {
            message.setStartDeliverTime(System.currentTimeMillis() + delayTimeUnit.toMinutes(delayTime));
        }
        return message;
    }

    private Message buildMessage(String topic, String tag, String key, String body) {
       return buildMessage(topic, tag, key, body, 0, null);
    }

    private Message buildMessage(String topic, String tag, String key, String body, String shardingKey) {
        Message message = buildMessage(topic, tag, key, body);
        message.setShardingKey(shardingKey);
        return message;
    }

    public void sendOneway(Message message) {
        CatTransactionManager.newTransaction(() -> {
            producerBean.sendOneway(message);
        }, RocketMQConstant.MQ_CAT_TYPE, RocketMQConstant.SEND_ONEWAY_MESSAGE);
    }

    public void sendAsync(Message message, SendCallback sendCallback) {
        CatTransactionManager.newTransaction(() -> {
            producerBean.sendAsync(message, sendCallback);
        }, RocketMQConstant.MQ_CAT_TYPE, RocketMQConstant.SEND_ASYNC_MESSAGE);
    }

}
