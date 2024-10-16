package com.example.crudrestful.service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.StringJoiner;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.example.crudrestful.dto.request.AuthenticationRequest;
import com.example.crudrestful.dto.request.IntrospectRequest;
import com.example.crudrestful.dto.request.LogoutRequest;
import com.example.crudrestful.dto.request.RefreshRequest;
import com.example.crudrestful.dto.response.AuthenticationResponse;
import com.example.crudrestful.dto.response.IntrospectResponse;
import com.example.crudrestful.entity.InvalidatedToken;
import com.example.crudrestful.entity.User;
import com.example.crudrestful.exception.AppException;
import com.example.crudrestful.exception.ErrorCode;
import com.example.crudrestful.repository.InvalidatedTokenRepository;
import com.example.crudrestful.repository.UserRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {
    UserRepository userRepository;
    InvalidatedTokenRepository invalidatedTokenRepository;

    @NonFinal
    @Value("${jwt.signerkey}")
    protected String SIGNER_KEY;

    @NonFinal
    @Value("${jwt.valid-duration}")
    protected long VALID_DURATION;

    @NonFinal
    @Value("${jwt.refreshable-duration}")
    protected long REFRESHABLE_DURATION;

    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        var token = request.getToken();
        boolean isValid = true;

        try {
            verifyToken(token, false);
        } catch (AppException e) {
            isValid = false;
        }

        return IntrospectResponse.builder().valid(isValid).build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        var user = userRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!authenticated) throw new AppException(ErrorCode.UNAUTHENTICATED);

        var token = generateToken(user);

        return AuthenticationResponse.builder().token(token).authenticate(true).build();
    }

    public void logout(LogoutRequest request) throws ParseException, JOSEException {
        try {

            // gửi vào hàm kiểm tra token 1 đoạn token còn thời gian
            // nếu token hợp lệ -> gán vào biến signToken
            var signToken = verifyToken(request.getToken(), true);

            // Lấy ra JWTID và thời gian tồn tại còn lại của token
            String jit = signToken.getJWTClaimsSet().getJWTID();
            Date expiryTime = signToken.getJWTClaimsSet().getExpirationTime();

            // Khởi tạo đối tượng invalidatedToken chứa các thuộc tính như jit và expiryTime
            InvalidatedToken invalidatedToken =
                    InvalidatedToken.builder().id(jit).expiryTime(expiryTime).build();

            // Lưu vào database
            invalidatedTokenRepository.save(invalidatedToken);
        } catch (AppException exception) {
            log.info("Token already expired");
        }
    }

    public AuthenticationResponse refreshToken(RefreshRequest request) throws ParseException, JOSEException {
        // Xác thực token còn hiệu lực
        var signedJWT = verifyToken(request.getToken(), true);

        // Lấy ra id và thời gian hết hạn của token
        var jit = signedJWT.getJWTClaimsSet().getJWTID();
        var expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();

        // Khởi tạo đối tượng invalidatedToken chứa các thuộc tính như id và thời gian hết hạn
        InvalidatedToken invalidatedToken =
                InvalidatedToken.builder().id(jit).expiryTime(expiryTime).build();

        // Lưu vào trong database
        invalidatedTokenRepository.save(invalidatedToken);

        // Lấy tên của user trong token
        var username = signedJWT.getJWTClaimsSet().getSubject();

        // Lấy user bằng phương thức tìm kiếm theo tên
        var user =
                userRepository.findByUsername(username).orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        // Tạo 1 token mới
        var token = generateToken(user);

        // Trả về đối tượng AuthenticationResponse có các thuộc tính token(mới tạo) và authenticate(true)
        return AuthenticationResponse.builder().token(token).authenticate(true).build();
    }

    private String generateToken(User user) {
        // Khởi tạo thuật toán bằng thuật toán mã hóa HS512
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        // Set các dữ liệu public ra cho người dùng
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername()) // Tên user
                .issuer("devteria.com") // domain
                .issueTime(new Date()) // Thời gian tạo token
                .expirationTime(
                        new Date( // Thời gian tồn tại của token
                                Instant.now()
                                        .plus(VALID_DURATION, ChronoUnit.SECONDS)
                                        .toEpochMilli()))
                .jwtID(UUID.randomUUID().toString()) // id của token
                .claim("scope", buildScope(user)) // chức vụ của token
                .build();

        // Lớp đại diện cho nội dung token muốn truyền tải và chữ kí mã hóa
        // chuyển đổi jwtClaimsSet thành dạng json
        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        // Đại diện cho một jwt đã được kí, bao gồm thuật toán mã hóa và cách chứa token đã được kí
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            // Kí jwt bằng thuật toán HMAC sử dụng key đã cài đặt trong application.properties
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            // Tuần tự hóa jwsObject thành 1 chuỗi string
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw new RuntimeException(e);
        }
    }

    private SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException {
        // Tạo đối tượng jwsVerifier với một khóa bí mật(Khóa chữ kí của jwt)
        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());

        // Chuyển đổi chuỗi token thành đối tượng SingnedJWT đại diện cho jwt đã kí
        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime =
                (isRefresh) // Nếu isRefresh bằng true Thời gian tính được cộng thêm dựa trên "REFRESHABLE_DURATION"
                        ? new Date(signedJWT
                                .getJWTClaimsSet()
                                .getIssueTime()
                                .toInstant()
                                .plus(REFRESHABLE_DURATION, ChronoUnit.SECONDS)
                                .toEpochMilli())
                        : signedJWT
                                .getJWTClaimsSet()
                                .getExpirationTime(); // ngược lại thời gian hết hạn là thời gian gốc của token

        // xác minh jwt với khóa bí mật, nếu token hợp lệ, chữ kí khớp thì trả về true
        var verified = signedJWT.verify(verifier);

        // Nếu token không hợp lệ hoặc thời gian hết hạn
        if (!(verified && expiryTime.after(new Date()))) throw new AppException(ErrorCode.UNAUTHENTICATED);

        // Kiểm tra xem JWTID có tồn tại trong bảng invalidatedToken
        if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID()))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        // Trả ra đối tượng signedJWT
        return signedJWT;
    }

    private String buildScope(User user) {
        StringJoiner stringJoiner = new StringJoiner(" ");

        if (!CollectionUtils.isEmpty(user.getRoles()))
            user.getRoles().forEach(role -> {
                stringJoiner.add("ROLE_" + role.getName());
                if (!CollectionUtils.isEmpty(role.getPermissions()))
                    role.getPermissions().forEach(permission -> stringJoiner.add(permission.getName()));
            });

        return stringJoiner.toString();
    }
}
