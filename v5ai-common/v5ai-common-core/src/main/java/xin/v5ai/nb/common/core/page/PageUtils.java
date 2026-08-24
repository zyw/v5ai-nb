//package xin.v5ai.nb.common.core.page;
//
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.List;
//
///**
// * 内存分页工具：对 Service 返回的全量列表按 PageQuery 切片，构建 PageResult。
// * 默认 pageNum=1、pageSize=Integer.MAX_VALUE（即返回全量），与不分页行为兼容。
// */
//public final class PageUtils {
//
//    private PageUtils() {
//    }
//
//    public static <T> PageResult<T> page(Collection<T> data, PageQuery query) {
//        List<T> list = data == null ? new ArrayList<>() : new ArrayList<>(data);
//        int pageNum = query == null || query.getPageNum() == null ? 1 : query.getPageNum();
//        int pageSize = query == null || query.getPageSize() == null ? Integer.MAX_VALUE : query.getPageSize();
//        if (pageNum <= 0) {
//            pageNum = 1;
//        }
//        if (pageSize <= 0) {
//            pageSize = Integer.MAX_VALUE;
//        }
//        long total = list.size();
//        int from = (pageNum - 1) * pageSize;
//        if (from >= list.size()) {
//            return PageResult.build(List.of(), total);
//        }
//        int to = Math.min(from + pageSize, list.size());
//        return PageResult.build(list.subList(from, to), total);
//    }
//}
