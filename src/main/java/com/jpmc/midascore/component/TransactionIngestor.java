package com.jpmc.midascore.component;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionIngestor {

    private static final Logger logger = LoggerFactory.getLogger(TransactionIngestor.class);

    private final DatabaseConduit databaseConduit;
    private final IncentiveService incentiveService;

    public TransactionIngestor(DatabaseConduit databaseConduit, IncentiveService incentiveService) {
        this.databaseConduit = databaseConduit;
        this.incentiveService = incentiveService;
    }

    @KafkaListener(topics = "${general.kafka-topic}")
    public void listen(Transaction transaction) {
        UserRecord sender = databaseConduit.findUser(transaction.getSenderId());
        UserRecord recipient = databaseConduit.findUser(transaction.getRecipientId());

        if (sender == null || recipient == null) return;
        if (sender.getBalance() < transaction.getAmount()) return;

        float incentive = incentiveService.getIncentive(transaction);

        sender.setBalance(sender.getBalance() - transaction.getAmount());
        recipient.setBalance(recipient.getBalance() + transaction.getAmount() + incentive);

        databaseConduit.save(sender);
        databaseConduit.save(recipient);
        databaseConduit.save(new TransactionRecord(sender, recipient, transaction.getAmount(), incentive));

        logger.info("Recipient {} balance now: {}", recipient.getName(), recipient.getBalance());
    }
}