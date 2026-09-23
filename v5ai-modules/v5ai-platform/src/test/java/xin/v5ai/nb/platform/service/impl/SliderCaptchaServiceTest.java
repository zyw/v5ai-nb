package xin.v5ai.nb.platform.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.web.config.properties.CaptchaProperties;
import xin.v5ai.nb.common.web.core.SliderCaptchaStore;
import xin.v5ai.nb.platform.domain.VerifyEnum;
import xin.v5ai.nb.platform.domain.vo.SliderCaptchaVO;
import xin.v5ai.nb.platform.service.IPlmSliderCaptchaService;

import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 滑块验证码服务单元测试：拼图生成、缺口校验容差、一次性消费。
 */
class SliderCaptchaServiceTest {

    private IPlmSliderCaptchaService service;
    private FakeStore store;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        CaptchaProperties properties = new CaptchaProperties();
        properties.setEnable(true);
        properties.setSliderThreshold(3);
        properties.setExpireMinutes(2);
        service = new PlmSliderCaptchaServiceImpl(properties, store);
    }

    @Test
    void generateReturnsValidImagesAndSavesGap() {
        SliderCaptchaVO vo = service.generate();

        assertNotNull(vo.uuid());
        assertNotNull(vo.background());
        assertNotNull(vo.puzzle());
        assertTrue(vo.y() >= 30);
        assertTrue(vo.y() + IPlmSliderCaptchaService.PIECE_SIZE <= IPlmSliderCaptchaService.HEIGHT - 20);
        // 缺口 x 仅存服务端，不随图片下发
        assertTrue(store.gap(vo.uuid()) >= IPlmSliderCaptchaService.PIECE_SIZE + 10);
        // 返回的是合法 PNG（魔数 89 50 4E 47）
        byte[] bytes = Base64.getDecoder().decode(vo.background());
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals((byte) 0x50, bytes[1]);
    }

    @Test
    void verifyAcceptsPositionWithinToleranceAndIsOneTime() {
        SliderCaptchaVO vo = service.generate();
        int gapX = store.gap(vo.uuid());

        // 误差在允许范围内通过
        assertEquals(VerifyEnum.OK,
                service.verify(vo.uuid(), (double) (gapX + IPlmSliderCaptchaService.TOLERANCE)));
        assertTrue(store.verified.containsKey(vo.uuid()));

        // 令牌一次性：再次校验报过期
        assertEquals(VerifyEnum.EXPIRED,
                service.verify(vo.uuid(), (double) gapX));
    }

    @Test
    void verifyRejectsWrongPosition() {
        SliderCaptchaVO vo = service.generate();
        int gapX = store.gap(vo.uuid());

        assertEquals(VerifyEnum.MISMATCH,
                service.verify(vo.uuid(), (double) (gapX + IPlmSliderCaptchaService.TOLERANCE + 1)));
        assertFalse(store.verified.containsKey(vo.uuid()));
    }

    @Test
    void verifyRejectsExpiredToken() {
        assertEquals(VerifyEnum.EXPIRED, service.verify("unknown-uuid", 100d));
        assertFalse(store.verified.containsKey("unknown-uuid"));
    }

    @Test
    void checkAndConsumeVerifiedIsOneTime() {
        SliderCaptchaVO vo = service.generate();
        service.verify(vo.uuid(), (double) store.gap(vo.uuid()));

        assertTrue(service.checkAndConsumeVerified(vo.uuid()));
        assertFalse(service.checkAndConsumeVerified(vo.uuid()));
        assertFalse(service.checkAndConsumeVerified("unknown-uuid"));
        assertFalse(service.checkAndConsumeVerified(null));
    }

    @Test
    void allPieceShapesStayWithinPieceBounds() {
        for (PlmSliderCaptchaServiceImpl.PieceShape shape : PlmSliderCaptchaServiceImpl.PieceShape.values()) {
            java.awt.geom.Rectangle2D bounds = shape.path().getBounds2D();
            assertTrue(bounds.getMinX() >= 0, shape + " 左越界: " + bounds);
            assertTrue(bounds.getMinY() >= 0, shape + " 上越界: " + bounds);
            assertTrue(bounds.getMaxX() <= IPlmSliderCaptchaService.PIECE_SIZE + 0.01, shape + " 右越界: " + bounds);
            assertTrue(bounds.getMaxY() <= IPlmSliderCaptchaService.PIECE_SIZE + 0.01, shape + " 下越界: " + bounds);
        }
    }

    @Test
    void generateWorksRepeatedlyWithRandomShapes() {
        for (int i = 0; i < 20; i++) {
            SliderCaptchaVO vo = service.generate();
            assertNotNull(vo.uuid());
            assertNotNull(vo.background());
            assertNotNull(vo.puzzle());
            assertTrue(store.gap(vo.uuid()) >= IPlmSliderCaptchaService.PIECE_SIZE + 10);
        }
    }

    /**
     * 内存版存储，模拟 Redis 一次性令牌行为。
     */
    private static final class FakeStore implements SliderCaptchaStore {

        private final Map<String, Integer> gaps = new HashMap<>();
        private final Map<String, Boolean> verified = new HashMap<>();

        Integer gap(String uuid) {
            return gaps.get(uuid);
        }

        @Override
        public void saveGap(String uuid, int gapX, Duration ttl) {
            gaps.put(uuid, gapX);
        }

        @Override
        public Integer getGap(String uuid) {
            return gaps.get(uuid);
        }

        @Override
        public void deleteGap(String uuid) {
            gaps.remove(uuid);
        }

        @Override
        public void markVerified(String uuid, Duration ttl) {
            verified.put(uuid, Boolean.TRUE);
        }

        @Override
        public boolean checkAndConsumeVerified(String uuid) {
            return verified.remove(uuid) != null;
        }
    }
}
