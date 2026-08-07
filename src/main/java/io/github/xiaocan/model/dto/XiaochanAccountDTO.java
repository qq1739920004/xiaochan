package io.github.xiaocan.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class XiaochanAccountDTO {
    @NotBlank(message = "账号名称不能为空")
    private String accountName;
    @NotNull(message = "silk_id 不能为空")
    private Long silkId;
    @JsonProperty("xVayne")
    @JsonAlias({"XVayne", "x_vayne"})
    @NotNull(message = "X-Vayne 不能为空")
    private Long xVayne;
    @JsonProperty("xSivir")
    private String xSivir;
    @NotNull(message = "请设置账号是否启用")
    private Boolean enabled = true;
}
