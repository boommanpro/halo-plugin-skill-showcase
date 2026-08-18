package run.halo.skillshowcase.finder;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.halo.skillshowcase.dto.SkillCategoryVo;
import run.halo.skillshowcase.dto.SkillVo;
import run.halo.skillshowcase.service.SkillCategoryService;
import run.halo.skillshowcase.service.SkillService;

@Component
@RequiredArgsConstructor
public class SkillFinder {

    private final SkillService skillService;
    private final SkillCategoryService categoryService;

    public List<SkillVo> listAll() {
        return skillService.listPublished().block();
    }

    public List<SkillVo> listByCategory(String category) {
        return skillService.listPublishedByCategory(category).block();
    }

    public SkillVo getByName(String name) {
        return skillService.getPublicByName(name).block();
    }

    public List<SkillCategoryVo> listCategories() {
        return categoryService.listAll().block();
    }
}