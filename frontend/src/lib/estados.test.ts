import { describe, expect, it, vi, afterEach } from "vitest";
import { formatoFecha, hoyLocal } from "./estados";

describe("fechas locales", () => {
  afterEach(() => vi.useRealTimers());

  it("hoyLocal usa la fecha local, no la de UTC", () => {
    // 16/11/2026 22:30 en Bolivia (UTC-4) = 17/11/2026 02:30 UTC
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 10, 16, 22, 30));
    expect(hoyLocal()).toBe("2026-11-16");
    expect(hoyLocal(1)).toBe("2026-11-17");
  });

  it("formatea aaaa-mm-dd como dd/mm/aaaa sin desplazar el día", () => {
    expect(formatoFecha("2026-11-16")).toBe("16/11/2026");
    expect(formatoFecha("2026-11-16T23:59:00")).toBe("16/11/2026");
    expect(formatoFecha(null)).toBe("—");
  });
});
