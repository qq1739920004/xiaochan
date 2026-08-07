package io.github.xiaocan.model;

import lombok.Data;

@Data
public class XiaochanAccountSnapshot {
    private Long upstreamUserId;
    private String nickname;
    private String phone;
    private String vipLevel;
    private int cardTotal;
    private int cardActive;
    private int cardExpired;
    private int redpackTotal;
    private int meituanRedpackTotal;
    private int elemeRedpackTotal;
    private int platformRedpackTotal;
}
