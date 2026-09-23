package xin.v5ai.nb.skill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.skill.domain.SkillFile;
import xin.v5ai.nb.skill.domain.vo.SkillFileVo;
import xin.v5ai.nb.skill.mapper.SkillFileMapper;
import xin.v5ai.nb.skill.service.ISkillFileService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillFileServiceImpl implements ISkillFileService {

    private final SkillFileMapper fileMapper;

    @Override
    public List<SkillFileVo> selectList(Long versionId) {
        return fileMapper.selectVoList(new LambdaQueryWrapper<SkillFile>()
                        .eq(SkillFile::getVersionId, versionId));
    }
}
