package com.wisla.fm.zabbixsim;

import com.wisla.fm.zabbixsim.config.SimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SimulatorProperties.class)
public class ZabbixSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZabbixSimulatorApplication.class, args);
    }
}
