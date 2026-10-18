import { buildItemPayload } from './itemForm.js'

export function applyRecommendedPrice(form,result){if(result?.status!=="ESTIMATE"||!Number.isFinite(Number(result.recommendedPrice)))return false;form.price=Number(result.recommendedPrice);return true}
export function buildPublishPayload(form){return buildItemPayload(form)}
