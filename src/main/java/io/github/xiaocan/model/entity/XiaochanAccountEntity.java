package io.github.xiaocan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("xiaochan_account")
public class XiaochanAccountEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    private String accountName;
    private Long silkId;
    private Long xVayne;
    private String xSivir;
    private Boolean enabled;
    private Long upstreamUserId;
    private String nickname;
    private String phone;
    private String vipLevel;
    private Integer cardTotal;
    private Integer cardActive;
    private Integer cardExpired;
    private Integer redpackTotal;
    private Integer meituanRedpackTotal;
    private Integer elemeRedpackTotal;
    private Integer platformRedpackTotal;
    private String refreshStatus;
    private String lastRefreshError;
    private LocalDateTime lastRefreshTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Boolean deleted;
}
