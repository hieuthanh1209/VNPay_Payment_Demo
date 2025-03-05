package com.example.digitalhib_payment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

//Thêm VNPAY SDK
import com.vnpay.authentication.VNP_AuthenticationActivity;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;


public class MainActivity extends AppCompatActivity {
    Button btnPay;
    EditText edtAmount;
    private static final String VNP_TMNCODE = "ABCDEFGH";  // Mã website VNPay cấp (Terminal ID)
    private static final String VNP_HASH_SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";  // Chuỗi bí mật VNPay cấp (Secret Key)
    private static final String VNP_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";  // URL thanh toán VNPay
    private static final String VNP_RETURNURL = "digitalhib://vnpay_return"; // Cấu hình DeepLink đúng
    private static final String SCHEME = "digitalhib";  // Scheme app

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        edtAmount = findViewById(R.id.edtAmount);
        btnPay = findViewById(R.id.btnPay);

        // Set sự kiện cho nút thanh toán (btnPay)
        btnPay.setOnClickListener(view -> {
            String inputText = edtAmount.getText().toString().trim();

            if (inputText.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập số tiền", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int amount = Integer.parseInt(inputText); // Chuyển thành số
                if (amount <= 0) {
                    Toast.makeText(this, "Số tiền phải lớn hơn 0", Toast.LENGTH_SHORT).show();
                } else {
                    openSdk(amount); // Truyền số tiền vào openSDK
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Vui lòng nhập số hợp lệ", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Hàm mở SDK thanh toán của VNPay
    public void openSdk(int amount) {
        String orderId = String.valueOf("DH_" + System.currentTimeMillis()); // Tạo mã đơn hàng duy nhất

        // Tạo URL thanh toán
        String paymentUrl = getPaymentUrl(amount, orderId);
        Log.d("VNPay", "Payment URL: " + paymentUrl);

        // Mở Activity của SDK thanh toán
        Intent intent = new Intent(this, VNP_AuthenticationActivity.class);
        intent.putExtra("url", paymentUrl); // Truyền URL thanh toán hợp lệ
        intent.putExtra("tmn_code", VNP_TMNCODE);
        intent.putExtra("scheme", SCHEME);
        intent.putExtra("is_sandbox", false); // true = môi trường test, false = live

        startActivity(intent);
    }
    // Cấu trúc của URL thanh toán
    /*
        BẮT BUỘC khai báo đầy đủ các tham số, nếu không VNPay sẽ báo lỗi "Dữ liệu gửi sang không đúng định dạng":
        https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?
            vnp_Amount=100000 (Số tiền)&
            vnp_Command=pay (Lệnh thanh toán)&
            vnp_CreateDate=20210801153333& (Ngày tạo đơn, format YYYY-mm-DD-HH-MM-SS)
            vnp_CurrCode=VND (Đơn vị tiền tệ)&
            vnp_IpAddr=127.0.0.1 (Địa chỉ IP của host, mặc định 127.0.0.1 nếu không lấy được Host IP)&
            vnp_Locale=vn (Mã vùng)&
            vnp_OrderInfo=Thanh+toan+don+hang+%3A5 (Tiêu đề của đơn hàng)&
            vnp_OrderType=other&
            vnp_ReturnUrl=https%3A%2F%2Fdomainmerchant.vn%2FReturnUrl&
            vnp_TmnCode=DEMOV210 (Terminal ID / Mã website)&
            vnp_TxnRef=5&
            vnp_Version=2.1.0&
            vnp_SecureHash = Checksum / Mã kiểm tra. Hash version hỗ trợ SHA256 và HMACSHA512
        Ngoài những tham số trên, VNPay còn hỗ trợ nhiều tham số khác như: vnp_ExpireDate
        Xem thêm trên: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html (Mục "Code cài đặt")
    */
    // Hàm tạo URL thanh toán
    public String getPaymentUrl(int amount, String orderId) {
        // Tạo đối tượng SimpleDateFormat để định dạng ngày và giờ
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
        // Tạo HashMap để lưu trữ các tham số cần thiết
        Map<String, String> vnp_Params = new HashMap<>();
        // Số tiền thanh toán
        vnp_Params.put("vnp_Amount", String.valueOf(amount * 100));
        // Lệnh thanh toán
        vnp_Params.put("vnp_Command", "pay");
        // Ngày tạo đơn hàng
        String createDate = sdf.format(new Date());
        vnp_Params.put("vnp_CreateDate", createDate);
        // Đơn vị tiền tệ: VND
        vnp_Params.put("vnp_CurrCode", "VND");
        // Lấy địa chỉ IP của host
        String vnp_IpAddr = getLocalIpAddress();
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);
        // Mã vùng
        vnp_Params.put("vnp_Locale", "vn");
        // Tiêu đề đơn hàng
        vnp_Params.put("vnp_OrderInfo", "Thanh toán đơn hàng " + orderId);
        // Loại đơn hàng
        vnp_Params.put("vnp_OrderType", "billpayment");
        // Terminal code
        vnp_Params.put("vnp_TmnCode", VNP_TMNCODE);
        // Return URL
        vnp_Params.put("vnp_ReturnUrl", VNP_RETURNURL);
        // Mã đơn hàng
        vnp_Params.put("vnp_TxnRef", orderId);
        // Phiên bản VNPay
        vnp_Params.put("vnp_Version", "2.1.0");
        // Hết hạn thanh toán
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 15); // Đặt thời gian hết hạn là 15 phút sau khi tạo đơn hàng
        String expireDate = sdf.format(calendar.getTime());
        vnp_Params.put("vnp_ExpireDate", expireDate);

        // Sắp xếp tham số theo thứ tự bảng chữ cái
        List<Map.Entry<String, String>> paramList = new ArrayList<>(vnp_Params.entrySet());
        paramList.sort(Map.Entry.comparingByKey());

        // Tạo chuỗi query (KHÔNG có vnp_SecureHash)
        StringBuilder queryBuilder = new StringBuilder();
        for (Map.Entry<String, String> param : paramList) {
            try {
                queryBuilder.append(param.getKey()).append("=")
                        .append(URLEncoder.encode(param.getValue(), "UTF-8")) // Đảm bảo giá trị được encode đúng
                        .append("&");
            } catch (Exception e) {
                Log.e("VNPay", "Encode Error: " + e.getMessage());
            }
        }
        String query = queryBuilder.substring(0, queryBuilder.length() - 1); // Loại bỏ dấu "&" cuối

        // Tạo chữ ký SHA512
        String vnp_SecureHash = hmacSHA512(VNP_HASH_SECRET, query);
        query += "&vnp_SecureHash=" + vnp_SecureHash; // Gắn chữ ký vào URL

        // In log để kiểm tra tham số
        Log.d("VNPay", "VNP_HASH_SECRET: " + VNP_HASH_SECRET);
        Log.d("VNPay", "vnp_Amount: " + vnp_Params.get("vnp_Amount"));
        Log.d("VNPay", "vnp_CreateDate: " + vnp_Params.get("vnp_CreateDate"));
        Log.d("VNPay", "vnp_TxnRef: " + vnp_Params.get("vnp_TxnRef"));

        // Trả về URL thanh toán
        return VNP_URL + "?" + query;
    }

    // Hàm lấy địa chỉ IP của host
    public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("VNPay", "Không thể lấy địa chỉ IP", ex);
        }
        return "127.0.0.1"; // Giá trị mặc định nếu không lấy được IP
    }

    // Hàm tạo chữ ký HMACSHA512
    public String hmacSHA512(String key, String data) {
        try {
            Mac sha512_HMAC = Mac.getInstance("HmacSHA512");
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            sha512_HMAC.init(secret_key);
            byte[] hash = sha512_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Chuyển byte array sang hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString().toUpperCase(); // VNPay yêu cầu uppercase
        } catch (Exception e) {
            Log.e("VNPay", "HMAC SHA512 Error: " + e.getMessage());
            return null;
        }
    }

    // Hàm xử lý DeepLink trả về
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // Cập nhật intent mới

        Uri data = intent.getData();
        if (data != null) {
            String vnp_ResponseCode = data.getQueryParameter("vnp_ResponseCode");

            if ("00".equals(vnp_ResponseCode)) {
                Toast.makeText(this, "Thanh toán thành công!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Thanh toán thất bại!", Toast.LENGTH_LONG).show();
            }
        }
    }

}