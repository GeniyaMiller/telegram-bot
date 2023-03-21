package pro.sky.telegrambot.listener;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.entity.NotificationTask;
import pro.sky.telegrambot.service.NotificationTaskService;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TelegramBotUpdatesListener implements UpdatesListener {

    private Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);

    private final Pattern pattern = Pattern.compile("([\\d\\\\.:\\s]{16})(\\s)([A-zА-я\\s\\d.,!:;]+)");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final TelegramBot telegramBot;
    private final NotificationTaskService notificationTaskService;

    public TelegramBotUpdatesListener(TelegramBot telegramBot, NotificationTaskService notificationTaskService) {
        this.telegramBot = telegramBot;
        this.notificationTaskService = notificationTaskService;
    }

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
    }

    @Override
    public int process(List<Update> updates) {
        try {updates.stream().filter(update -> update.message() != null)
                .forEach(update -> {
            logger.info("Processing update: {}", update);
            Long chatId = update.message().chat().id();
                    String text = update.message().text();
            if ("/start".equals(text)) {
                sendMessage(chatId,"Привет! Создай запрос в формате:\n 01.01.2000 10:00 Текст задачи");
            } else if (text != null) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    LocalDateTime dateTime = parse(matcher.group(1));
                    if (Objects.isNull(dateTime)) {
                        sendMessage(chatId, "Некорректный формат даты/времени");
                    } else {
                        String txt = matcher.group(3);
                        NotificationTask notificationTask = new NotificationTask();
                        notificationTask.setChatId(chatId);
                        notificationTask.setMessage(txt);
                        notificationTask.setNotificationDateTime(dateTime);
                        notificationTaskService.save(notificationTask);
                        sendMessage(chatId,"Задача успешно запланирована!");
                    }
                } else {
                    sendMessage(chatId, "Некорректный формат сообщения!");
                }
            }
            });
        } catch (Exception e){
            e.printStackTrace();
        }
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

        private void sendMessage (Long chatId, String message) {
            SendMessage sendMessage = new SendMessage(chatId, message);
            SendResponse response = telegramBot.execute(sendMessage);
            if(!response.isOk()) {
                logger.error("Error during sending message: {}",response.description());
            }
        }

        private LocalDateTime parse (String dateTime) {
            try {
                return LocalDateTime.parse(dateTime, dateTimeFormatter);
            } catch (DateTimeParseException e) {
                return null;
            }
        }

}
