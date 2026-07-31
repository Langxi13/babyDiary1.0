export const normalizeSpace = (space = {}) => ({ ...space, editable: space.role !== 'VIEWER' })
