/** Keep this rule in sync with backend DagValidator.compatible(). */
export function canConnect(outputType:string,inputType:string):boolean{
  return inputType==='ANY'||outputType===inputType||(outputType==='IMAGE_ASSET_LIST'&&inputType==='ASSET_REF_LIST');
}
