// Today's date as YYYY-MM-DD, in the *browser's* zone.
//
// `toISOString().slice(0, 10)` reads as the obvious way to do this and is wrong: it converts to
// UTC first, so after 20:00 Eastern every date field defaults to tomorrow. en-CA formats dates
// as YYYY-MM-DD natively, which is also the wire format the API expects.
export function today(): string {
  return new Date().toLocaleDateString('en-CA');
}
