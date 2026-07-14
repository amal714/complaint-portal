package com.mla.portal.controller;

import com.mla.portal.model.Complaint;
import com.mla.portal.model.User;
import com.mla.portal.repository.ComplaintRepository;
import com.mla.portal.repository.UserRepository;
import com.mla.portal.service.OtpService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Controller
public class PortalController {

    private final UserRepository userRepository;
    private final ComplaintRepository complaintRepository;
    private final OtpService otpService;

    // Replace this with the MLA's actual WhatsApp phone number (with country code, no "+" or spaces)
    private final String MLA_WHATSAPP_NUMBER = "917012701600";

    public PortalController(UserRepository userRepository, ComplaintRepository complaintRepository, OtpService otpService) {
        this.userRepository = userRepository;
        this.complaintRepository = complaintRepository;
        this.otpService = otpService;
    }

    // 1. Show Registration / Login Form
    @GetMapping("/register")
    public String showRegisterPage() {
        return "register";
    }

    // 2. Process Registration and trigger Mock OTP
    @PostMapping("/register")
    public String registerUser(@RequestParam String name,
                               @RequestParam String phone,
                               @RequestParam String region,
                               HttpSession session,
                               Model model) {
        // If user doesn't exist, save them. If they do, treat this as a login attempt.
        Optional<User> existingUser = userRepository.findByPhone(phone);
        User user = existingUser.orElseGet(() -> {
            User newUser = new User();
            newUser.setName(name);
            newUser.setPhone(phone);
            newUser.setRegion(region);
            return userRepository.save(newUser);
        });

        // Generate OTP and push it to console
        otpService.generateOtp(user.getPhone());

        // Save phone in session to keep track of who is verifying
        session.setAttribute("pendingPhone", user.getPhone());
        return "redirect:/verify";
    }

    // 3. Show OTP Verification Screen
    @GetMapping("/verify")
    public String showVerifyPage(HttpSession session, Model model) {
        if (session.getAttribute("pendingPhone") == null) {
            return "redirect:/register";
        }
        return "verify";
    }

    // 4. Validate OTP
    @PostMapping("/verify")
    public String verifyOtp(@RequestParam String otp, HttpSession session, Model model) {
        String phone = (String) session.getAttribute("pendingPhone");

        if (phone != null && otpService.validateOtp(phone, otp)) {
            User user = userRepository.findByPhone(phone).orElseThrow();
            user.setVerified(true);
            userRepository.save(user);

            // Promote to an active authenticated session
            session.setAttribute("currentUser", user);
            session.removeAttribute("pendingPhone");
            return "redirect:/dashboard";
        }

        model.addAttribute("error", "Invalid OTP code. Please try again.");
        return "verify";
    }

    // 5. Citizen Dashboard: View & File Complaints
    @GetMapping("/dashboard")
    public String showDashboard(HttpSession session, Model model) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return "redirect:/register";
        }

        List<Complaint> complaints = complaintRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
        model.addAttribute("user", currentUser);
        model.addAttribute("complaints", complaints);

        return "dashboard";
    }

    // 6. Submit a Ported Complaint
    @PostMapping("/complaint")
    public String submitComplaint(@RequestParam String message, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return "redirect:/register";
        }

        Complaint complaint = new Complaint();
        complaint.setUser(currentUser);
        complaint.setMessage(message);
        complaintRepository.save(complaint);

        return "redirect:/dashboard";
    }

    // 7. Instant WhatsApp Redirection Logic
    @GetMapping("/share/whatsapp/{complaintId}")
    public String redirectToWhatsApp(@PathVariable Long complaintId, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return "redirect:/register";
        }

        Complaint complaint = complaintRepository.findById(complaintId).orElseThrow();

        // Construct a clean, structured message text for the MLA
        String rawMessage = String.format(
                "Grievance Portal Alert\n\nCitizen: %s\nRegion: %s\nPhone: %s\n\nComplaint:\n%s",
                currentUser.getName(), currentUser.getRegion(), currentUser.getPhone(), complaint.getMessage()
        );

        // URL encode the message text so spaces and breaks don't corrupt the browser link
        String encodedMessage = URLEncoder.encode(rawMessage, StandardCharsets.UTF_8);
        String whatsappUrl = "https://wa.me/" + MLA_WHATSAPP_NUMBER + "?text=" + encodedMessage;

        return "redirect:" + whatsappUrl;
    }

    // Logout Helper
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/register";
    }
}