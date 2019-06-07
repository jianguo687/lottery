package me.zohar.lottery.agent.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import cn.hutool.core.util.StrUtil;
import me.zohar.lottery.agent.domain.RebateAndOdds;
import me.zohar.lottery.agent.domain.RebateAndOddsSituation;
import me.zohar.lottery.agent.param.AddOrUpdateRebateAndOddsParam;
import me.zohar.lottery.agent.param.AgentOpenAnAccountParam;
import me.zohar.lottery.agent.repo.RebateAndOddsRepo;
import me.zohar.lottery.agent.repo.RebateAndOddsSituationRepo;
import me.zohar.lottery.agent.vo.RebateAndOddsSituationVO;
import me.zohar.lottery.agent.vo.RebateAndOddsVO;
import me.zohar.lottery.common.exception.BizError;
import me.zohar.lottery.common.exception.BizException;
import me.zohar.lottery.common.param.PageParam;
import me.zohar.lottery.common.valid.ParamValid;
import me.zohar.lottery.common.vo.PageResult;
import me.zohar.lottery.constants.Constant;
import me.zohar.lottery.useraccount.domain.UserAccount;
import me.zohar.lottery.useraccount.repo.UserAccountRepo;

@Validated
@Service
public class AgentService {

	@Autowired
	private RebateAndOddsRepo rebateAndOddsRepo;

	@Autowired
	private RebateAndOddsSituationRepo rebateAndOddsSituationRepo;

	@Autowired
	private UserAccountRepo userAccountRepo;

	@Transactional(readOnly = true)
	public List<RebateAndOddsVO> findAllRebateAndOdds() {
		List<RebateAndOdds> rebateAndOddses = rebateAndOddsRepo.findAll(Sort.by(Sort.Order.desc("rebate")));
		return RebateAndOddsVO.convertFor(rebateAndOddses);
	}

	@Transactional(readOnly = true)
	public PageResult<RebateAndOddsSituationVO> findRebateAndOddsSituationByPage(PageParam param) {
		Specification<RebateAndOddsSituation> spec = new Specification<RebateAndOddsSituation>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public Predicate toPredicate(Root<RebateAndOddsSituation> root, CriteriaQuery<?> query,
					CriteriaBuilder builder) {
				List<Predicate> predicates = new ArrayList<Predicate>();
				return predicates.size() > 0 ? builder.and(predicates.toArray(new Predicate[predicates.size()])) : null;
			}
		};
		Page<RebateAndOddsSituation> result = rebateAndOddsSituationRepo.findAll(spec,
				PageRequest.of(param.getPageNum() - 1, param.getPageSize(), Sort.by(Sort.Order.asc("rebate"))));
		PageResult<RebateAndOddsSituationVO> pageResult = new PageResult<>(
				RebateAndOddsSituationVO.convertFor(result.getContent()), param.getPageNum(), param.getPageSize(),
				result.getTotalElements());
		return pageResult;
	}

	@Transactional
	public void resetRebateAndOdds(@NotEmpty List<AddOrUpdateRebateAndOddsParam> params) {
		Map<String, String> map = new HashMap<>();
		for (AddOrUpdateRebateAndOddsParam param : params) {
			String key = param.getRebate() + "/" + param.getOdds();
			if (map.get(key) != null) {
				throw new BizException(BizError.不能设置重复的返点赔率);
			}
			map.put(key, key);
		}

		rebateAndOddsRepo.deleteAll();
		Date now = new Date();
		for (AddOrUpdateRebateAndOddsParam param : params) {
			RebateAndOdds rebateAndOdds = param.convertToPo(now);
			rebateAndOddsRepo.save(rebateAndOdds);
		}
	}

	@Transactional
	public void delRebateAndOdds(@NotNull Double rebate, @NotNull Double odds) {
		RebateAndOdds rebateAndOdds = rebateAndOddsRepo.findTopByRebateAndOdds(rebate, odds);
		rebateAndOddsRepo.delete(rebateAndOdds);
	}

	@Transactional(readOnly = true)
	public RebateAndOddsVO findRebateAndOdds(@NotNull Double rebate, @NotNull Double odds) {
		RebateAndOdds rebateAndOdds = rebateAndOddsRepo.findTopByRebateAndOdds(rebate, odds);
		return RebateAndOddsVO.convertFor(rebateAndOdds);
	}

	@ParamValid
	@Transactional
	public void addOrUpdateRebateAndOdds(AddOrUpdateRebateAndOddsParam param) {
		// 新增
		if (StrUtil.isBlank(param.getId())) {
			RebateAndOdds rebateAndOdds = rebateAndOddsRepo.findTopByRebateAndOdds(param.getRebate(), param.getOdds());
			if (rebateAndOdds != null) {
				throw new BizException(BizError.该返点赔率已存在);
			}
			RebateAndOdds newRebateAndOdds = param.convertToPo(new Date());
			rebateAndOddsRepo.save(newRebateAndOdds);
		}
		// 修改
		else {
			RebateAndOdds rebateAndOdds = rebateAndOddsRepo.findTopByRebateAndOdds(param.getRebate(), param.getOdds());
			if (rebateAndOdds != null && !rebateAndOdds.getId().equals(param.getId())) {
				throw new BizException(BizError.该返点赔率已存在);
			}
			RebateAndOdds editRebateAndOdds = rebateAndOddsRepo.getOne(param.getId());
			BeanUtils.copyProperties(param, editRebateAndOdds);
			rebateAndOddsRepo.save(editRebateAndOdds);
		}
	}

	/**
	 * 代理开户
	 */
	@ParamValid
	@Transactional
	public void agentOpenAnAccount(AgentOpenAnAccountParam param) {
		UserAccount userAccount = userAccountRepo.getOne(param.getInviterId());
		if (!(Constant.账号类型_管理员.equals(userAccount.getAccountType())
				|| Constant.账号类型_管理员.equals(userAccount.getAccountType()))) {
			throw new BizException(BizError.只有管理员或代理才能进行代理开户操作);
		}

	}

}