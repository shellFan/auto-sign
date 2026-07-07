package com.autosign.service;

import com.autosign.entity.WeiboSuperTopic;
import com.autosign.mapper.WeiboSuperTopicMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class WeiboTopicService {

    @Autowired
    private WeiboSuperTopicMapper topicMapper;

    public List<WeiboSuperTopic> listByAccountId(Long accountId) {
        LambdaQueryWrapper<WeiboSuperTopic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WeiboSuperTopic::getAccountId, accountId)
                .eq(WeiboSuperTopic::getEnableStatus, 1)
                .orderByAsc(WeiboSuperTopic::getSort);
        return topicMapper.selectList(wrapper);
    }

    public List<WeiboSuperTopic> listByUserId(Long userId) {
        LambdaQueryWrapper<WeiboSuperTopic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WeiboSuperTopic::getUserId, userId)
                .orderByAsc(WeiboSuperTopic::getSort);
        return topicMapper.selectList(wrapper);
    }

    public WeiboSuperTopic getById(Long id) {
        return topicMapper.selectById(id);
    }

    public boolean create(WeiboSuperTopic topic) {
        topic.setLastSignStatus(0);
        topic.setCreateTime(LocalDateTime.now());
        topic.setUpdateTime(LocalDateTime.now());
        return topicMapper.insert(topic) > 0;
    }

    public boolean update(WeiboSuperTopic topic) {
        topic.setUpdateTime(LocalDateTime.now());
        return topicMapper.updateById(topic) > 0;
    }

    public boolean delete(Long id) {
        return topicMapper.deleteById(id) > 0;
    }

    public boolean updateSignResult(Long id, Integer status) {
        WeiboSuperTopic topic = new WeiboSuperTopic();
        topic.setId(id);
        topic.setLastSignStatus(status);
        topic.setLastSignDate(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        topic.setUpdateTime(LocalDateTime.now());
        return topicMapper.updateById(topic) > 0;
    }

    public boolean enable(Long id) {
        WeiboSuperTopic topic = new WeiboSuperTopic();
        topic.setId(id);
        topic.setEnableStatus(1);
        topic.setUpdateTime(LocalDateTime.now());
        return topicMapper.updateById(topic) > 0;
    }

    public boolean disable(Long id) {
        WeiboSuperTopic topic = new WeiboSuperTopic();
        topic.setId(id);
        topic.setEnableStatus(0);
        topic.setUpdateTime(LocalDateTime.now());
        return topicMapper.updateById(topic) > 0;
    }

    public void initDefaultTopics(Long userId, Long accountId) {
        String[][] defaults = {
                {"易烊千玺", "https://weibo.com/p/1008087d4c56ccdd21d10f32eea97a29975b52"},
                {"子瑜", "https://weibo.com/p/1008087a8b9c0d1e2f3a"}
        };

        for (String[] topicInfo : defaults) {
            LambdaQueryWrapper<WeiboSuperTopic> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(WeiboSuperTopic::getUserId, userId)
                    .eq(WeiboSuperTopic::getAccountId, accountId)
                    .eq(WeiboSuperTopic::getTopicName, topicInfo[0]);
            if (topicMapper.selectCount(wrapper) == 0) {
                WeiboSuperTopic topic = new WeiboSuperTopic();
                topic.setUserId(userId);
                topic.setAccountId(accountId);
                topic.setTopicName(topicInfo[0]);
                topic.setTopicUrl(topicInfo[1]);
                topic.setEnableStatus(1);
                topic.setSort(0);
                topic.setCreateTime(LocalDateTime.now());
                topic.setUpdateTime(LocalDateTime.now());
                topicMapper.insert(topic);
            }
        }
    }

    public boolean hasNotSignedToday(Long accountId) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        LambdaQueryWrapper<WeiboSuperTopic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WeiboSuperTopic::getAccountId, accountId)
                .eq(WeiboSuperTopic::getEnableStatus, 1)
                .and(w -> w.ne(WeiboSuperTopic::getLastSignDate, today)
                        .or().isNull(WeiboSuperTopic::getLastSignDate));
        return topicMapper.selectCount(wrapper) > 0;
    }
}
