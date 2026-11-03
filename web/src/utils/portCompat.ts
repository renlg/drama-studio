/** Keep this rule in sync with backend DagValidator.compatible(). */
export function canConnect(outputType:string,inputType:string):boolean{
  const assetOutputs=['IMAGE_ASSET_LIST','VIDEO_ASSET_LIST','AUDIO_ASSET_LIST','CHARACTER','SCENE'];
  return inputType==='ANY'||outputType===inputType||(inputType==='ASSET_REF_LIST'&&assetOutputs.includes(outputType));
}
