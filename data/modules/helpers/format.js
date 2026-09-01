function capitalize(str) {
    if (!str) return '';
    return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}

function uppercase(str) {
    return str ? str.toUpperCase() : '';
}

module.exports = {
    capitalize: capitalize,
    uppercase: uppercase
};