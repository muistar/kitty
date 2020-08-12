package com.cxytiandi.kitty.rocketmq;

import java.util.Date;
import java.util.List;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.SendResult;
import com.aliyun.openservices.shade.com.alibaba.fastjson.JSONObject;
import com.aliyun.openservices.shade.org.apache.commons.lang3.StringUtils;
import com.cxytiandi.kitty.common.cat.CatTransactionManager;
import com.cxytiandi.kitty.lock.DistributedLock;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProcessMessageTask {

	private TransactionMQService transactionMQService;

	private RocketMQProducer rocketMQProducer;

	private DistributedLock distributedLock;

	public ProcessMessageTask(TransactionMQService transactionMQService, RocketMQProducer rocketMQProducer, DistributedLock distributedLock) {
		this.transactionMQService = transactionMQService;
		this.rocketMQProducer = rocketMQProducer;
		this.distributedLock = distributedLock;
		start();
	}

	public void start() {
		Thread th = new Thread(new Runnable() {
			
			public void run() {
				while(true) {
					processByLock();
				}
			}
		});
		th.start();
	}

	private void processByLock() {
		distributedLock.lock("transaction_message", 1000 * 60, () -> {
			process();
			return null;
		}, () -> {
			log.warn("获取锁失败");
			return null;
		});
	}
	
	private void process() {
		List<TransactionMessage> messages = transactionMQService.listWatingSendMessage(100);
		CatTransactionManager.newTransaction(() -> {
			doProcess(messages);
		}, RocketMQConstant.MQ_CAT_TYPE, RocketMQConstant.DISPATCH);
	}

	private void doProcess(List<TransactionMessage> messages) {
		messages.parallelStream().forEach(message -> {
			SendResult sendResult = null;
			try {
				sendResult = sendMessage(message);
			} catch (Exception e) {
				log.error("消息发送失败: {}", message.getId(), e);
			} finally {
				if (sendResult != null && StringUtils.isNotBlank(sendResult.getMessageId())) {
					message.setMessageId(sendResult.getMessageId());
					message.setStatus(1);
				}
				message.setSendTime(new Date());
				message.setSendCount(message.getSendCount() + 1);
				transactionMQService.updateMessage(message);
			}
		});
	}
	
	private SendResult sendMessage(TransactionMessage message) {
		SendResult sendResult = null;
		Message msg = JSONObject.parseObject(message.getMessage(), Message.class);
		if (RocketMQMessageTypeEnum.NORMAL.getType().equals(message.getMessageType())) {
			sendResult = rocketMQProducer.sendMessage(msg, false);
		}

		if (RocketMQMessageTypeEnum.ORDER.getType().equals(message.getMessageType())) {
			sendResult = rocketMQProducer.sendOrderMessage(msg, msg.getShardingKey(),false);
		}

		if (RocketMQMessageTypeEnum.DELAY.getType().equals(message.getMessageType())) {
			sendResult = rocketMQProducer.sendMessage(msg, false);
		}

		return sendResult;
	}
}
