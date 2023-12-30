package org.example;

import org.checkerframework.checker.units.qual.K;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.json.*;

public class WeatherBot extends TelegramLongPollingBot{
    private static Map<Long,String> chosenCity = new HashMap<>();
    @Override
    public void onUpdateReceived(Update update) {
        Long userId = update.getMessage().getChatId();
        if(!chosenCity.containsKey(userId)){
           chosenCity.put(userId,null);
        }
        if (update.hasMessage() && update.getMessage().hasText()) {
            String textFromUser = update.getMessage().getText();
            if (textFromUser.equals("/start")){
                chosenCity.put(userId,null);
                answerForStart(userId.toString());
                KeyboardHelper keyboardHelper = new KeyboardHelper();
                ReplyKeyboard replyKeyboard = keyboardHelper.buildMainMenu();
                sendMessageWithKeyboard(userId.toString(),"Оберіть місто або опишіть вручну⤵️",replyKeyboard);
            } else if (textFromUser.equals("❌ Скасувати")) {
                if(chosenCity.get(userId)!=null){
                    chosenCity.put(userId,null);
                }
                KeyboardHelper keyboardHelper = new KeyboardHelper();
                ReplyKeyboard replyKeyboard = keyboardHelper.buildMainMenu();
                sendMessageWithKeyboard(userId.toString(),"Оберіть місто або опишіть вручну⤵️",replyKeyboard);
            } else if(chosenCity.get(userId)!=null){
                String userFirstName = update.getMessage().getFrom().getFirstName();
                System.out.println("Ім'я:" + userFirstName);
                String apiKey = "0c01e3a74a821d41e43c9bde0ac14c7a";
                String city = chosenCity.get(userId);
                String apiUrl = "http://api.openweathermap.org/data/2.5/forecast?q=" + city + "&appid=" + apiKey;

                try {
                    String jsonResponse = sendHttpGet(apiUrl);
                    if (jsonResponse.equals("Error, not found")) {
                        sendMess(userId.toString(), "Такого міста не знайдено");
                        chosenCity.put(userId,null);
                        return;
                    }
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    Date date = dateFormat.parse(textFromUser);
                    String s = extractWeatherForDay(jsonResponse, date, userId.toString());
                    if (s.equals("ERROR")){
                        sendMess(userId.toString(), "Відсутній прогноз на дану дату");
                    }
                    askForDate(userId);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ParseException e) {
                    sendMess(userId.toString(), "Не правильно введена дата");
                    askForDate(userId);
                }

            }else {
                chosenCity.put(userId,textFromUser);
                String apiKey = "0c01e3a74a821d41e43c9bde0ac14c7a";
                String city = chosenCity.get(userId);
                String apiUrl = "http://api.openweathermap.org/data/2.5/forecast?q=" + city + "&appid=" + apiKey;

                try {
                    String jsonResponse = sendHttpGet(apiUrl);
                    if (jsonResponse.equals("Error, not found")) {
                        sendMess(userId.toString(), "Такого міста не знайдено");
                        chosenCity.put(userId,null);
                        KeyboardHelper keyboardHelper = new KeyboardHelper();
                        ReplyKeyboard replyKeyboard = keyboardHelper.buildMainMenu();
                        sendMessageWithKeyboard(userId.toString(),"Оберіть місто або опишіть вручну⤵️",replyKeyboard);
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                askForDate(userId);
            }
        } else {
            sendMess(userId.toString(),"Не зрозумів Вас");
        }
    }
    private void askForDate(Long userId){
        KeyboardHelper keyboardHelper = new KeyboardHelper();
        Calendar today = Calendar.getInstance();
        List<String> dateList = getDateListForNext6Days(today);
        ReplyKeyboard replyKeyboard = keyboardHelper.buildCitiesMenu(dateList);
        sendMessageWithKeyboard(userId.toString(),"Виберіть дату:",replyKeyboard);
    }
    private List<String> getDateListForNext6Days(Calendar startDate) {
        List<String> dateStrings = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        for (int i = 0; i < 6; i++) {
            // Копіюємо поточну дату
            Calendar currentDate = (Calendar) startDate.clone();

            // Додаємо i днів до поточної дати
            currentDate.add(Calendar.DAY_OF_MONTH, i);

            // Перетворюємо об'єкт Date в рядок і додаємо до списку
            dateStrings.add(dateFormat.format(currentDate.getTime()));
        }

        return dateStrings;
    }
    private void answerForStart(String userId){
        sendMess(userId, "Привіт, я бот, який розповідає про погоду у різних містах");
    }
    private void sendMess(String userId, String mess) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(userId)
                .text(mess)
                .build();
        try {
            this.sendApiMethod(sendMessage);
        } catch (TelegramApiException e) {
            System.out.println("Exception when sending message: " + e);
        }
    }
    private void sendMessageWithKeyboard(String userId, String mess, ReplyKeyboard replyKeyboard){
        SendMessage sendMessage = SendMessage
                .builder()
                .text(mess)
                .chatId(userId)
                //Other possible parse modes: MARKDOWNV2, MARKDOWN, which allows to make text bold, and all other things
                .parseMode(ParseMode.HTML)
                .replyMarkup(replyKeyboard)
                .build();
        try {
            this.sendApiMethod(sendMessage);
        } catch (TelegramApiException e) {
            System.out.println("Exception when sending message: "+ e);
        }
    }
    private String extractWeatherForDay(String jsonResponse, Date desiredDate,String userId) {
        JSONObject jsonObject = new JSONObject(jsonResponse);

        // Отримуємо масив з прогнозами
        JSONArray forecasts = jsonObject.getJSONArray("list");
        // Перебираємо прогнози та знаходимо той, що відповідає заданому дню
        for (int i = 0; i < forecasts.length(); i++) {
            JSONObject forecast = forecasts.getJSONObject(i);
            String forecastDateStr = forecast.getString("dt_txt");

            try {
                // Перетворюємо рядок дати в об'єкт Date
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date forecastDate = dateFormat.parse(forecastDateStr);
                // Порівнюємо дату прогнозу з бажаною датою
                if (isSameDay(forecastDate, desiredDate)) {
                    // Отримуємо дані для заданого дня
                    JSONObject main = forecast.getJSONObject("main");
                    double temperature = main.getDouble("temp");
                    double humidity = main.getDouble("humidity");

                    JSONObject wind = forecast.getJSONObject("wind");
                    double windSpeed = wind.getDouble("speed");

                    JSONArray weather = forecast.getJSONArray("weather");
                    JSONObject weatherInfo = weather.getJSONObject(0);
                    String precipitation = weatherInfo.getString("description");
                    String answ ="";
                    answ+="Температура: " + Math.round(temperature-273.15) + "°C\n";
                    answ+="Вологість: " + humidity + "%\n";
                    answ+="Швидкість вітру: " + windSpeed + " m/s\n";
                    answ+="Опади\\хмарність: " + precipitation;
                    sendMessageWithKeyboard(userId,answ,null);
                    return answ; // Виходимо з циклу, якщо знайшли відповідний день
                }
            } catch (Exception e) {
                sendMess(userId, "Неправильно введена дата");
            }
        }

        System.out.println("Прогноз для бажаної дати не знайдено.");
        return "ERROR";
    }
    private static boolean isSameDay(Date date1, Date date2) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);

        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);

        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH);
    }
    private static String sendHttpGet(String apiUrl) throws IOException {
        // Встановлення з'єднання
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Отримання відповіді
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            return response.toString();
        } else {
           return "Error, not found";
        }
    }
    @Override
    public String getBotUsername() {
        return "WeatherBlastBot";
    }

    @Override
    public String getBotToken() {
        return "6673820775:AAFnh6yT3w8P3-M1k2imTlRMz7w9zWg0V5E";
    }
}
