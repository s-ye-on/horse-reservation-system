# DTO Rule

## Required

- Controller는 JPA Entity를 요청이나 응답 타입으로 사용하지 않는다.
- 구조화된 request body는 `*Request` DTO로 받는다.
- API 응답은 `*Response` DTO로 반환한다.
- path variable과 query parameter의 단순 타입, body가 없는 응답은 DTO를 강제하지 않는다.
- 불변 Request/Response DTO는 `record`를 기본으로 한다.
- DTO에 `Optional` 필드를 사용하지 않는다.

```text
Request DTO -> Controller -> Service -> Response DTO
```
