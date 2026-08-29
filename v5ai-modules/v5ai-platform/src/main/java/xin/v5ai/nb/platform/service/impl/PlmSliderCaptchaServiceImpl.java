package xin.v5ai.nb.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.web.config.properties.CaptchaProperties;
import xin.v5ai.nb.common.web.core.SliderCaptchaStore;
import xin.v5ai.nb.platform.domain.VerifyEnum;
import xin.v5ai.nb.platform.domain.vo.SliderCaptchaVO;
import xin.v5ai.nb.platform.service.IPlmSliderCaptchaService;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图片滑块验证码服务：生成拼图与缺口，校验用户拖动位置。
 * 缺口 x 坐标仅存于服务端（{@link SliderCaptchaStore}），客户端无法获取。
 */
@Service
@RequiredArgsConstructor
public class PlmSliderCaptchaServiceImpl implements IPlmSliderCaptchaService {

    private final CaptchaProperties captchaProperties;
    private final SliderCaptchaStore store;

    /**
     * 生成一张滑块拼图：随机缺口位置，背景图与拼图块以 PNG base64 返回。
     *
     * @return 拼图令牌与图片数据
     */
    @Override
    public SliderCaptchaVO generate() {
        int gapX = RandomUtil.randomInt(PIECE_SIZE + 10, WIDTH - PIECE_SIZE - 10);
        int gapY = RandomUtil.randomInt(30, HEIGHT - PIECE_SIZE - 20);
        String uuid = IdUtil.fastSimpleUUID();

        BufferedImage background = createBackground();
        // 拼图块需取自未挖缺口的原图，保证与缺口区域图案一致
        BufferedImage puzzle = cropPiece(background, gapX, gapY);
        drawNotch(background, gapX, gapY);

        store.saveGap(uuid, gapX, Duration.ofMinutes(expireMinutes()));
        return new SliderCaptchaVO(uuid, toBase64(background), toBase64(puzzle), gapY);
    }

    /**
     * 校验用户拖动位置：与缺口 x 坐标误差不超过 {@link #TOLERANCE} 视为通过。
     * 校验通过后标记该令牌已验证，供登录门控消费；拼图一次性使用，失败或过期需刷新。
     *
     * @param uuid 拼图令牌
     * @param x    用户拖动后拼图块的横向位置
     * @return 校验结果
     */
    @Override
    public VerifyEnum verify(String uuid, Double x) {
        Integer gapX = store.getGap(uuid);
        store.deleteGap(uuid);
        if (gapX == null) {
            return VerifyEnum.EXPIRED;
        }
        if (x == null || Math.abs(gapX - Math.round(x)) > TOLERANCE) {
            return VerifyEnum.MISMATCH;
        }
        store.markVerified(uuid, Duration.ofMinutes(expireMinutes()));
        return VerifyEnum.OK;
    }

    /**
     * 登录门控：消费已验证标记。返回 false 表示该令牌未通过滑块验证。
     *
     * @param uuid 拼图令牌
     * @return 是否存在且已消费已验证标记
     */
    @Override
    public boolean checkAndConsumeVerified(String uuid) {
        return StringUtils.isNotBlank(uuid) && store.checkAndConsumeVerified(uuid);
    }

    private int expireMinutes() {
        Integer expireMinutes = captchaProperties.getExpireMinutes();
        return expireMinutes == null || expireMinutes <= 0 ? 2 : expireMinutes;
    }

    /**
     * 绘制程序化背景：渐变底 + 随机色块，避免依赖外部图片资源。
     */
    private BufferedImage createBackground() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ThreadLocalRandom random = RandomUtil.getRandom();
            GradientPaint gradient = new GradientPaint(
                    0, 0, randomPastel(random),
                    WIDTH, HEIGHT, randomPastel(random));
            g.setPaint(gradient);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            for (int i = 0; i < 16; i++) {
                g.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256), 40 + random.nextInt(60)));
                int size = 12 + random.nextInt(56);
                int x = random.nextInt(WIDTH);
                int y = random.nextInt(HEIGHT);
                if (random.nextBoolean()) {
                    g.fillOval(x, y, size, size);
                } else {
                    g.fillRect(x, y, size, size);
                }
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * 从背景图扣出圆角拼图块并描边。
     */
    private BufferedImage cropPiece(BufferedImage source, int gapX, int gapY) {
        BufferedImage piece = new BufferedImage(PIECE_SIZE, PIECE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = piece.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape shape = new RoundRectangle2D.Float(0, 0, PIECE_SIZE, PIECE_SIZE, 8, 8);
            g.setClip(shape);
            g.drawImage(source, -gapX, -gapY, null);
            g.setClip(null);
            g.setColor(new Color(255, 255, 255, 180));
            g.setStroke(new BasicStroke(2f));
            g.draw(shape);
        } finally {
            g.dispose();
        }
        return piece;
    }

    /**
     * 在背景图上绘制缺口（半透明遮罩 + 描边），指示拼图应放回的位置。
     */
    private void drawNotch(BufferedImage image, int gapX, int gapY) {
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape shape = new RoundRectangle2D.Float(gapX, gapY, PIECE_SIZE, PIECE_SIZE, 8, 8);
            g.setColor(new Color(0, 0, 0, 90));
            g.fill(shape);
            g.setColor(new Color(255, 255, 255, 160));
            g.setStroke(new BasicStroke(2f));
            g.draw(shape);
        } finally {
            g.dispose();
        }
    }

    private Color randomPastel(ThreadLocalRandom random) {
        return new Color(120 + random.nextInt(120), 120 + random.nextInt(120), 120 + random.nextInt(120));
    }

    private String toBase64(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("滑块拼图生成失败", e);
        }
    }
}
