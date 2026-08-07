package io.github.xiaocan.service;

import io.github.xiaocan.model.dto.XiaochanAccountDTO;
import io.github.xiaocan.model.vo.XiaochanAccountVO;

import java.util.List;

public interface XiaochanAccountService {
    List<XiaochanAccountVO> listAccounts(boolean refresh);

    XiaochanAccountVO saveAccount(Integer id, XiaochanAccountDTO dto);

    XiaochanAccountVO refreshAccount(Integer id);

    void disableAccount(Integer id);

    XiaochanAccountVO findOwnedAccount(Integer accountId, Integer userId);
}
