package com.ssafy.S14P21A205;

import com.ssafy.S14P21A205.config.ClockConfig;
import com.ssafy.S14P21A205.config.RedisTtlProperties;
import java.util.TimeZone;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(RedisTtlProperties.class)
@SpringBootApplication
public class S14P21A205 {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(ClockConfig.APP_ZONE_ID));
		SpringApplication.run(S14P21A205.class, args);
	}

}
