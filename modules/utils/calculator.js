var math = require('utils/math');

function calculate(operation, a, b) {
    if (operation === 'add') {
        return math.add(a, b);
    } else if (operation === 'multiply') {
        return math.multiply(a, b);
    }
    return 0;
}

module.exports = {
    calculate: calculate
};