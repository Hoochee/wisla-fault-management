const fs = require("fs");
const path = require("path");
const root = "C:/Project/wislaFaultManagement/frontend";
const w = (p, c) => {
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, c, "utf8");
};
