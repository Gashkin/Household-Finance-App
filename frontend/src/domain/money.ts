const euro = new Intl.NumberFormat("en-IE", {
  style: "currency",
  currency: "EUR",
});

export function money(minor: number): string {
  if (!Number.isSafeInteger(minor))
    throw new RangeError("Amount must be safe integer cents");
  const cents = BigInt(minor);
  const absolute = cents < 0n ? -cents : cents;
  const fraction = (absolute % 100n).toString().padStart(2, "0");
  // Whole euros remain exactly representable; never divide cents as a floating point value.
  const formatted = euro
    .formatToParts(Number(absolute / 100n))
    .map((part) => (part.type === "fraction" ? fraction : part.value))
    .join("");
  return (cents < 0n ? "-" : "") + formatted;
}

export function parseMoney(value: string, label: string): number {
  if (!/^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/.test(value)) {
    throw new RangeError(
      label + " must be a non-negative euro amount with up to two decimals",
    );
  }
  const [whole, fraction = ""] = value.split(".");
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  if (!Number.isSafeInteger(cents))
    throw new RangeError(label + " is too large");
  return cents;
}

export function minorToInput(minor: number): string {
  if (!Number.isSafeInteger(minor) || minor < 0)
    throw new RangeError("Cash amount is not usable");
  const euros = Math.floor(minor / 100);
  const cents = String(minor % 100).padStart(2, "0");
  return euros + "." + cents;
}
