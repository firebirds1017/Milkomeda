package com.github.yizzuide.milkomeda.jupiter;

import com.github.yizzuide.milkomeda.universe.context.WebContext;
import com.github.yizzuide.milkomeda.universe.exception.NotImplementException;
import com.github.yizzuide.milkomeda.universe.extend.jdbc.CamelCaseColumnMapRowMapper;
import com.github.yizzuide.milkomeda.universe.parser.url.URLPlaceholderParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * JupiterScopeRuleEngine
 * 具有请求领域上下文的规则引擎
 *
 * @author yizzuide
 * @since 3.5.0
 * Create at 2020/05/19 14:39
 */
@Slf4j
public class JupiterScopeRuleEngine extends AbstractJupiterRuleEngine {

    private final URLPlaceholderParser urlPlaceholderParser = new URLPlaceholderParser();

    private final CamelCaseColumnMapRowMapper camelCaseColumnMapRowMapper = new CamelCaseColumnMapRowMapper();

    @Autowired
    private JupiterProperties props;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SuppressWarnings("rawtypes")
    @Override
    public boolean run(String ruleName) {
        List<JupiterRuleItem> ruleItemList = ruleCacheMap.get(ruleName);
        try {
            for (JupiterRuleItem ruleItem : ruleItemList) {
                JupiterExpressionCompiler compiler = JupiterCompilerPool.get(ruleItem.getSyntax());
                if (compiler == null) {
                    throw new NotImplementException("Can't find type implement with JupiterCompilerType." + ruleItem.getSyntax());
                }
                Boolean pass;
                if (ruleItem.getDomain() == null) {
                    pass = compiler.compile(ruleItem.getMatch(), null, Boolean.TYPE);
                    assert pass != null;
                    if (!pass) {
                        return false;
                    }
                    continue;
                }
                String filter = ruleItem.getFilter();
                // 解析过滤表达式
                if (filter != null && filter.contains("{$")) {
                    Map<String, List<String>> placeHolders = urlPlaceholderParser.grabPlaceHolders(filter);
                    filter = urlPlaceholderParser.parse(filter, WebContext.getRequest(), null, null, placeHolders);
                }
                // 构建查询
                String sql = JupiterSqlBuilder.build(ruleItem, filter);
                // 解析匹配表达式
                if (ruleItem.isMulti()) {
                    List<Map<String, Object>> rows = props.isMatchCamelCase() ? jdbcTemplate.query(sql, camelCaseColumnMapRowMapper) : jdbcTemplate.queryForList(sql);
                    pass = compiler.compile(ruleItem.getMatch(), rows, Boolean.TYPE);
                } else {
                    Map row = props.isMatchCamelCase() ? jdbcTemplate.queryForObject(sql, camelCaseColumnMapRowMapper) : jdbcTemplate.queryForMap(sql);
                    pass = compiler.compile(ruleItem.getMatch(), row, Boolean.TYPE);
                }
                assert pass != null;
                if (!pass) {
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("Jupiter rule engine execute error with msg: {}", e.getMessage(), e);
        }
        return true;
    }
}
