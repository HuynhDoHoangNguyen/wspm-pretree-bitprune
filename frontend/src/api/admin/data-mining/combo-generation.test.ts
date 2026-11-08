/** @jest-environment node */
import { runInThisContext } from 'vm';
import { setWdmAuthAdapter } from './index';
import { comboGenerationApi } from './combo-generation';
import { comboInputApi } from './combo-input';
Object.assign(globalThis,runInThisContext('({ fetch, Headers, Response })'));
const id='10000000-0000-4000-8000-000000000001';
let fetchMock: jest.Mock;
const response=(data: unknown)=>new Response(JSON.stringify({ code:'SUCCESS',message:'Request completed successfully.',data,requestId:'request-1' }),{ status:200,headers:{ 'Content-Type':'application/json','X-Request-Id':'request-1' } });
beforeEach(()=>{fetchMock=jest.fn();globalThis.fetch=fetchMock;setWdmAuthAdapter({ getAccessToken:()=> 'shared-token' });});
afterEach(()=>setWdmAuthAdapter(null));
test('both native clients use the shared bearer adapter, including command context only',async()=>{
  fetchMock.mockResolvedValueOnce(response({ runId:id,snapshotId:id,publicationDigest:'a'.repeat(64),patternCount:'2',dictionaryCount:'3',inputStatus:'VERIFIED_PUBLISHED_INPUT',generationStatus:'READY_FOR_PREVIEW',blockers:[] }));
  expect((await comboInputApi.read(id)).generationStatus).toBe('READY_FOR_PREVIEW');
  expect(new Headers(fetchMock.mock.calls[0][1].headers).get('Authorization')).toBe('Bearer shared-token');
  fetchMock.mockResolvedValueOnce(response({ status:'INSERTED',issues:[],batchId:'batch',storedGroups:2 }));
  await comboGenerationApi.submit('signed');
  expect(new Headers(fetchMock.mock.calls[1][1].headers).get('Authorization')).toBe('Bearer shared-token');
  expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ contextToken:'signed' });
  expect(fetchMock.mock.calls[1][1].credentials).toBe('omit');
});
test('rejects forged command result and preserves the server authentication denial',async()=>{
  fetchMock.mockResolvedValueOnce(response({ status:'INSERTED',issues:[],batchId:null,storedGroups:2 }));
  await expect(comboGenerationApi.submit('signed')).rejects.toMatchObject({ code:'INVALID_GENERATION_RESPONSE' });
  setWdmAuthAdapter(null);fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ status:401,code:'AUTHENTICATION_REQUIRED',message:'Authentication required.',path:'/api/v1/admin/combo-generation/submit',requestId:'request-1',details:[] }),{ status:401,headers:{ 'Content-Type':'application/json','X-Request-Id':'request-1' } }));
  await expect(comboGenerationApi.submit('signed')).rejects.toMatchObject({ status:401 });
  expect(new Headers(fetchMock.mock.calls[1][1].headers).has('Authorization')).toBe(false);
  expect(fetchMock).toHaveBeenCalledTimes(2);
});
test('loads exact run dictionary and sends only selection quantities and bounds', async () => {
  fetchMock.mockResolvedValueOnce(response({ runId: id, policyVersion: 'TV2_SELECTED_CATALOG_V2', maxDistinctProducts: 4000, maxQuantity: 4294967295, maxBatchItems: 4000,
    products: [{ itemId: 27, productId: 'P', name: 'Historical', sku: 'SKU' }] }));
  const options = await comboGenerationApi.options(id);
  expect(options.products[0].itemId).toBe(27);
  const selection = { requiredItems: [{ itemId: 27, quantity: 4294967295 }], minDistinctProducts: 2, maxDistinctProducts: 5 };
  fetchMock.mockResolvedValueOnce(response({ status: 'EMPTY', issues: [], examined: 3, accepted: 0, rejected: 3, groups: 0, eligible: 0,
    topPatternIds: [], anchors: [], reasonCounts: {}, referencePrices: {}, contextToken: null, policyVersion: 'TV2_SELECTED_CATALOG_V2', proposals: [] }));
  await comboGenerationApi.preview(id, null, 5, 'identity', undefined, selection);
  expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ runId: id, anchorProductId: null, k: 5, requestIdentity: 'identity', selection });
  fetchMock.mockResolvedValueOnce(response({ runId: id.replace(/1$/, '2'), policyVersion: 'TV2_SELECTED_CATALOG_V2', maxDistinctProducts: 4000, maxQuantity: 4294967295, maxBatchItems: 4000, products: [] }));
  await expect(comboGenerationApi.options(id)).rejects.toMatchObject({ code: 'INVALID_GENERATION_RESPONSE' });
});
test('category selection sends category identities without selecting concrete products', async () => {
  fetchMock.mockResolvedValueOnce(response({ runId: id, policyVersion: 'TV2_SELECTED_CATEGORIES_V3', maxDistinctProducts: 4000, maxQuantity: 4294967295, maxBatchItems: 4000,
    categories: [{ categoryId: 'laptops', name: 'Laptop', productCount: 4 }] }));
  expect((await comboGenerationApi.categoryOptions(id)).categories[0].productCount).toBe(4);
  const categorySelection = { requiredCategories: [{ categoryId: 'laptops', quantity: 2 }], minDistinctProducts: 2, maxDistinctProducts: 4 };
  fetchMock.mockResolvedValueOnce(response({ status: 'EMPTY', issues: [], examined: 3, accepted: 0, rejected: 3, groups: 0, eligible: 0,
    topPatternIds: [], anchors: [], reasonCounts: {}, referencePrices: {}, contextToken: null, policyVersion: 'TV2_SELECTED_CATEGORIES_V3', proposals: [] }));
  await comboGenerationApi.previewCategories(id, null, 5, 'id', undefined, categorySelection);
  const body = JSON.parse(fetchMock.mock.calls[1][1].body);
  expect(body.categorySelection).toEqual(categorySelection); expect(body).not.toHaveProperty('selection');
});
