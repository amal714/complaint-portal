package com.mla.portal.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class OtpService {
    // Stores phone -> generated OTP mapping temporarily
    private final Map<String, String> otpStorage = new HashMap<>();

    public String generateOtp(String phone) {
        String otp = String.format("%04d", new Random().nextInt(10000));
        otpStorage.put(phone, otp);

        // Outputting directly to console for quick testing
        System.out.println("==========================================");
        System.out.println("MOCK SMS SEND TO: " + phone);
        System.out.println("YOUR OTP IS: " + otp);
        System.out.println("==========================================");

        return otp;
    }

    public boolean validateOtp(String phone, String code) {
        if (otpStorage.containsKey(phone) && otpStorage.get(phone).equals(code)) {
            otpStorage.remove(phone); // Clear OTP after single use
            return true;
        }
        return false;
    }
}