import i18n from "../i18n";

const CODE_MAP: Record<string, string> = {
  "booking.not-available": "booking:errors.notAvailable",
  "booking.checkout-after-checkin": "booking:errors.badDates",
  "contract.already-signed": "booking:errors.alreadySigned",
  "review.already-exists": "property:errors.reviewExists",
  "agency.already-exists": "user:errors.agencyExists",
  "report.unsupported-format": "reports:errors.badFormat",
};

export function serverMsg(error: unknown, fallbackKey: string): string {
  const data = (error as { response?: { data?: { code?: string; message?: string } } })
    .response?.data;

  if (data?.code && CODE_MAP[data.code]) {
    return i18n.t(CODE_MAP[data.code]);
  }
  if (data?.message) {
    return data.message;
  }
  return i18n.t(fallbackKey);
}