package com.splitwise.service;

import com.splitwise.dto.AuthResponseDTO;
import com.splitwise.dto.LoginRequestDTO;
import com.splitwise.dto.RegisterRequestDTO;
import com.splitwise.entity.User;
import com.splitwise.repository.UserRepository;
import com.splitwise.security.JwtService;
//import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    public String register(RegisterRequestDTO requestDTO){
        if(userRepository.findByEmail(requestDTO.getEmail()).isPresent()){
            throw new RuntimeException("Email already registered");
        }
        User user=new User();
        user.setName(requestDTO.getName());
        user.setEmail(requestDTO.getEmail());
        user.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
        userRepository.save(user);
        return "User registered successfully";
    }

    public AuthResponseDTO login(LoginRequestDTO loginRequestDTO) {
        User user=userRepository.findByEmail(loginRequestDTO.getEmail())
                .orElseThrow(()->new RuntimeException("Email not found"));
        boolean passwordMatch=passwordEncoder.matches(loginRequestDTO.getPassword(), user.getPassword());
        if(!passwordMatch) throw new RuntimeException("Invalid credentials");
//        return "Login successful!"
        String token=jwtService.generateJWT(user.getEmail());
        return new AuthResponseDTO(token);
    }

}
