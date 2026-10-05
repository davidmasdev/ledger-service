// Reads a PostToolUse hook payload from stdin and prints a -DspotlessFiles regex
// for the edited file, or nothing if it isn't a .java file.
let input = "";
process.stdin.on("data", (chunk) => (input += chunk));
process.stdin.on("end", () => {
  const file = (JSON.parse(input || "{}").tool_input || {}).file_path || "";
  if (!file.endsWith(".java")) return;

  const escapeRegex = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  // Match on the last path segments with either separator, so Windows and Unix paths both work.
  const tail = file.split(/[\\/]/).slice(-4).map(escapeRegex).join("[\\\\/]");
  process.stdout.write(".*" + tail);
});
